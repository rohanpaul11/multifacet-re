package evaluation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SentenceErrorExamples2013 {
  /**
   * This prints out, per relation, the positive sentences with the lowest 
   * scores (false negatives) and the negative sentences with the highest scores
   * (false positives).
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    String keyFn = args[0];
    String predictFn = args[1];
    String candidatesFn = args[2];
    
    Set<String> positives = new HashSet<String>();
    Set<String> negatives = new HashSet<String>();
    
    BufferedReader br = new BufferedReader(new FileReader(keyFn));
    for (String line; (line = br.readLine()) != null;) {
//      6       SF13_ENG_001:per:age    LTW_ENG_20090727.0007   44      3563-3564       3551-3560,954-971       3489-3678       C       C       C       C       4
            /*
          Column 1: Response number [generated by NIST]

    Column 2: SF query ID + slot name
    Column 3: A single doc ID for a document in the source corpus
    Column 4: A slot filler (possibly normalized, e.g., for dates;
              otherwise, should appear in the provenance document)
    Column 5: Start-end character offsets for representative mentions
              used to extract/normalize filler. If two strings were
              used, they are represented as two, comma-separated
              offset pairs [from submission Column 6]
    Column 6: Start-end character offsets for representative mentions
              used to extract/normalize query entity.
    Column 7: Start-end character offsets of clause(s)/sentence(s) in
              justification.
    Column 8: LDC Assessment of Col 5 (filler offsets):
                C - Correct
                W - Wrong
                X - Inexact
                I - Ignore
    Column 9: LDC Assessment of Col 6 (query entity offsets):
                C - Correct
                W - Wrong
                X - Inexact
                I - Ignore
                0 - No Response (for {per,org}:alternate_names only)
    Coumn 10: LDC Assessment of Col 7 (relation justification offsets):
                as above
    Column 11: LDC Assessment of Col 4 (slot filler value
               correctness), with respect to the justification region
               defined by Cols 5-7:
                C - Correct
                W - Wrong
                X - Inexact
                R - Redundant with KB
                I - Ignore
    Column 12: LDC Equivalence class of Col 4 (slot filler) if Col 11
               is Correct or Redundant
*/
  /* Distribution over annotations in key (top 10):
  19401 W W W W
   3018 C C C C
    562 I I I I
    526 C C C R
    430 W W W C
    387 C X C C
    386 X C C X
    293 C C L C
    181 W W W X
    173 C W C C
     */

      String[] parts = line.split("\t");
      String qid = parts[1].split(":", 2)[0];
      String rel = parts[1].split(":", 2)[1];
      String docid = parts[2];
      String slotFiller = parts[3];
      String label = parts[7] + parts[8] + parts[9] + parts[10];

      String tuple = qid + "\t" + rel + "\t" + slotFiller + "\t" + docid;
      if ("CCCC".equals(label) || "CCCR".equals(label)) {
        positives.add(tuple);
      } else if ("WWWW".equals(label)) {
        negatives.add(tuple);
      }
    }
    br.close();

    System.out.println("Positives in key: " + positives.size());
    System.out.println("Negatives in key: " + negatives.size());

    
    // This maps relations to positive tuples with minimum scores.
    Map<String, String> minPositivesTuples = new TreeMap<String, String>();
    // This maps relations to the corresponding scores.
    Map<String, Double> minPositivesScores = new TreeMap<String, Double>();
    Map<String, String> maxNegativesTuples = new TreeMap<String, String>();
    Map<String, Double> maxNegativesScores = new TreeMap<String, Double>();
    
    br = new BufferedReader(new FileReader(predictFn));
    for (String line; (line = br.readLine()) != null;) {
      // SF_ENG_041      org:alternate_names     Washington Post LTW_ENG_20070505.0016.LDC2009T13.3      7       10      1       3       -1.1342196
      String[] parts = line.split("\t");
      String qid = parts[0];
      String rel = parts[1];
      String response = parts[2];
// old docid encoding:
//      String docid = parts[3].replaceFirst("\\.[0-9]+$", "");
// new docid encoding:
      String docid = parts[3].split(":", 2)[0];
      String tuple = qid + "\t" + rel + "\t" + response + "\t" + docid;

      // Avoid all ambiguity:
      boolean isPositive = positives.contains(tuple) && !negatives.contains(tuple);
      boolean isNegative = negatives.contains(tuple) && !positives.contains(tuple);
      
      double score = Double.parseDouble(parts[8]);
      double maxNegScore = maxNegativesScores.containsKey(rel) ? 
          maxNegativesScores.get(rel) : 0.0;
      double minPosScore = minPositivesScores.containsKey(rel) ? 
          minPositivesScores.get(rel) : 0.0;
      
      if (isNegative && score > maxNegScore) {
        maxNegativesScores.put(rel, score);
        maxNegativesTuples.put(rel, tuple);
      }
      if (isPositive && score < minPosScore) {
        minPositivesScores.put(rel, score);
        minPositivesTuples.put(rel, tuple);
      }
    }
    br.close();
    
    System.out.println("Max negatives:");
    //SF_ENG_073      org:parents     Carnival Corp.  APW_ENG_20070319.1047.LDC2009T13.14     0       1       6       8       Carnival Cruise Lines is part of Carnival Corp. , the world 's largest cruise group .
    br = new BufferedReader(new FileReader(candidatesFn));
    for (String line; (line = br.readLine()) != null;) {
      String[] parts = line.split("\t");
      String qid = parts[0];
      String rel = parts[1];
      String response = parts[2];
// old docid encoding:
//      String docid = parts[3].replaceFirst("\\.[0-9]+$", "");
// new docid encoding:
      String docid = parts[3].split(":", 2)[0];
      String tuple = qid + "\t" + rel + "\t" + response + "\t" + docid;
      if (tuple.equals(maxNegativesTuples.get(rel))) {
        System.out.println(line);
      }
    }
    br.close();
    System.out.println("Min positives:");
    br = new BufferedReader(new FileReader(candidatesFn));
    for (String line; (line = br.readLine()) != null;) {
      String[] parts = line.split("\t");
      String qid = parts[0];
      String rel = parts[1];
      String response = parts[2];
// old docid encoding:
//      String docid = parts[3].replaceFirst("\\.[0-9]+$", "");
// new docid encoding:
      String docid = parts[3].split(":", 2)[0];
      String tuple = qid + "\t" + rel + "\t" + response + "\t" + docid;
      if (tuple.equals(minPositivesTuples.get(rel))) {
        System.out.println(line);
      }
    }
    br.close();
  }
}