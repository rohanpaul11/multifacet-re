import pickle
import torch
import os
import sys
sys.path.insert(0, sys.path[0]+'/..')
from utils.utils_testing import lc_pred_dist, compute_cosine_sim, max_cosine_given_sim
import argparse


parser = argparse.ArgumentParser(description="Finding the nearest neighbors patterns")
parser.add_argument('--emb_dir', type=str, default='./output/patterns/',
                    help='folder of embeddings produced by model on train set')
parser.add_argument('--input_patterns', type=str, default='input_patterns.txt',
                    help='file name for set of patterns for which we wish to find nearest neighbors')
parser.add_argument('--save', type=str,  default='similar_patterns.txt',
                    help='file name where we wish to store the nearest neighbors')
parser.add_argument('--topk', type=int, default=5,
                    help='the number of nearest neighbors to find')
parser.add_argument('--L1_loss_B', type=float, default=0.2,
                    help='L1 loss coefficient')


args = parser.parse_args()

# Device to use (always use cuda if available)
DEVICE = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print("Using", DEVICE)

L1_loss_B = args.L1_loss_B
topk = args.topk


# Milestone run dir: 
# run31_vbasis_nornds11_rare_l3_autosgdlrpt1_autow_pt2_tgtlr1_autoavg_preavg_maxopt_autofixed-b5-kb11

# OUTDIR = "../output/run31_vbasis_nornds11_rare_l3_autosgdlr1_autow_pt1_tgtlr10_autoavg_preavg_maxopt-b5-kb11/"
# OUTDIR = "../output/run31_vbasis_nornds11_rare_l3_autosgdlrpt1_autow_pt2_tgtlr1_autoavg_preavg_maxopt_autofixed-b5-kb11/"
# KB_DIR = "kb_rels"
# IDX2KB_FILE = "idx2kb_dict.pkl"

OUTDIR = args.emb_dir
PAT_DIR = "patterns"
BASIS_PRED_FILE = "basis_pred.npy"
PAT_BASIS_PRED_FILE = "pat_basis_pred.pt"
EMB_FILE = "emb.npy"
IDX2PAT_FILE = "idx2pat_dict.pkl"
IDX2EP_FILE = "idx2ep_dict.pkl"
# FREEBASE_MAP = "data/en-freebase_wiki_cat_title_map.txt"
SAVE_FILE = os.path.join(OUTDIR, PAT_DIR, args.save)
INPUT_PATTERNS_FILE = os.path.join("output", args.input_patterns)

# Load patterns produced by model
patterns=torch.load(os.path.join(OUTDIR, PAT_DIR, PAT_BASIS_PRED_FILE), map_location=DEVICE)
with open(os.path.join(OUTDIR, PAT_DIR, IDX2PAT_FILE), 'rb') as fin:
    idx2pat = pickle.load(fin)
# Load entity pairs that co-occur with the training patterns
with open(os.path.join(OUTDIR, PAT_DIR, IDX2EP_FILE), 'rb') as fin:
    idx2ep = pickle.load(fin)

pat2idx = {pat:i for i, pat in enumerate(idx2pat.values())}
pat_texts = list(idx2pat.values())

# # Load freebase map mapping codes to freebase entities
# freebase_map = {}
# with open(FREEBASE_MAP, 'r') as fin:
#     for line in fin:
#         parts = line.strip().split('\t')
#         freebase_map[parts[1]] = parts[0]        
#         assert len(parts) == 2, "Got length: {}".format(len(parts))

#         eps = [idx2ep[pat_idx] for pat_idx in idx2pat]

# # Util functions for code to entity conversion
# def convert_fbentity(e):
#     if e in freebase_map:
#         return freebase_map[e]
#     return e

# def convert(eplist):
#     return ['\t'.join(list(map(convert_fbentity, ep.split('\t')))) for ep in eplist]

# Compute entailment scores for a source pattern against targets provided
def get_pat_entailment_scores_single_source(source, targets):
    specific_basis_pred = source.expand_as(targets)
    general_basis_pred = targets
    
    # print(specific_basis_pred.shape, general_basis_pred.shape)
    # kmeans sim scores
    cos_scores_basis_s2g, cos_scores_basis_g2s = compute_cosine_sim(specific_basis_pred, general_basis_pred)
    # kmeans: specific --> general
    max_cos_scores_s2g = max_cosine_given_sim(cos_scores_basis_s2g)
    # kmeans: general --> specific
    max_cos_scores_g2s = max_cosine_given_sim(cos_scores_basis_g2s)
    # kmeans: avg
    max_cos_scores_avg = (max_cos_scores_s2g + max_cos_scores_g2s)/2
    # kmeans: entailment score
    kmeans_entail = (max_cos_scores_g2s - max_cos_scores_s2g) * max_cos_scores_avg
    # kmeans: sign and direction aligned
    kmeans_sign = (max_cos_scores_g2s > max_cos_scores_s2g) * max_cos_scores_avg
    # sc dist scores
    # sc: specific --> general
    lcd_s2g_scores = lc_pred_dist(general_basis_pred, specific_basis_pred, None, L1_loss_B, DEVICE)            
    # sc: general --> specific
    lcd_g2s_scores = lc_pred_dist(specific_basis_pred, general_basis_pred, None, L1_loss_B, DEVICE)
    # sc: avg
    lcd_avg_scores = (lcd_s2g_scores + lcd_g2s_scores)/2
    # sc: entailment score
    lcd_entail = (lcd_g2s_scores - lcd_s2g_scores) * lcd_avg_scores
    # sc: sign and dir aligned
    lcd_sign = (lcd_g2s_scores > lcd_s2g_scores) * lcd_avg_scores
        
    scores = torch.cat([max_cos_scores_s2g.view(-1, 1), \
                        max_cos_scores_g2s.view(-1, 1), \
                        max_cos_scores_avg.view(-1, 1), \
                        kmeans_entail.view(-1, 1), \
                        kmeans_sign.view(-1, 1), \
                        lcd_s2g_scores.view(-1, 1), \
                        lcd_g2s_scores.view(-1, 1), \
                        lcd_avg_scores.view(-1, 1), \
                        lcd_entail.view(-1, 1), \
                        lcd_sign.view(-1, 1)], dim=1)
       
    return scores

# Finds the k nearest neighbors for eah nice pattern
def print_topk_nearest_pats(pats_to_visualize, all_patterns, topk, pat2idx, pat_texts, outf):   
    score_names = ["kmeans_s2g", "kmeans_g2s", "kmeans_avg", "kmeans_entail", "kmeans_sign", \
                   "sc_s2g", "sc_g2s", "sc_avg", "sc_entail", "sc_sign",]    
    for pat in pats_to_visualize:
        print(pat)
        outf.write("{}\n".format(pat))
        i = pat2idx[pat]
        scores = get_pat_entailment_scores_single_source(all_patterns[i], all_patterns)
        top_val_kmeans, top_index_kmeans = torch.topk(torch.cat([scores[:i, :], scores[i+1:, :]])[:, :5], topk, dim=0, sorted=False)
        top_index_kmeans = torch.where(top_index_kmeans >= i, top_index_kmeans+1, top_index_kmeans)
        top_val_sc, top_index_sc = torch.topk(torch.cat([scores[:i, :], scores[i+1:, :]])[:, 5:], topk, dim=0, sorted=False, largest=False)
        top_index_sc = torch.where(top_index_sc >= i, top_index_sc+1, top_index_sc)
        top_val = torch.cat([top_val_kmeans, top_val_sc], dim=1)
        top_index = torch.cat([top_index_kmeans, top_index_sc], dim=1)
        nns = [[(pat_texts[j], top_val[ridx][cidx].cpu().item()) for cidx, j in enumerate(i)] \
               for ridx, i in enumerate(top_index.tolist())]
        for b, l in enumerate(nns, start=1):
            outf.write('{}-nearest:\n'.format(b))
            outf.write(';\n'.join(["\t{}: ({}, {:.4f})".format(score_name, *score) for score_name, score in zip(score_names, l)]))
            outf.write('\n')
        outf.write('\n')

input_patterns = []
with open(INPUT_PATTERNS_FILE, 'r') as fin:
    for pat in fin:
        pat = pat.rstrip()
        input_patterns.append(pat)
        
# Find and save the computed nearest neighbors
if os.path.exists(SAVE_FILE):
    os.remove(SAVE_FILE)
with open(SAVE_FILE, 'a') as outf:
    print_topk_nearest_pats(input_patterns, patterns, topk, pat2idx, pat_texts, outf)