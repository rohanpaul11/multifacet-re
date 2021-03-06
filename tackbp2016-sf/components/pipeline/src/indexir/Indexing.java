package indexir;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import corpus.CorpusDocument;
import corpus.CorpusReader;

public class Indexing {
  public static final String ID = "id";
  public static final String CORPUS = "corpus";
  public static final String FILENAME = "filename";
  public static final String CONTENTS = "contents";

  public static void main(String[] args) throws IOException {
    // TODO: Use CorpusReader
    if (args.length != 5 && args.length != 6) {
      System.out.println("java Indexing <inputDir|fileNameList> <corpusName> <addFiles=true|false> <indexDir> [<docidList>]");
      System.out.println("inputDir|fileNameList: directory traversed for corpus sgml files|explicit list with filenames");
      System.out.println("corpusName:            corpus identifier stored in the index to restrict retrieval");
      System.out.println("addFiles:              whether index is build from scratch, or documents are added");
      System.out.println("indexDir:              directory where index is located");
      System.out.println("docidList:             file with list of doc ids to be indexed (optional, all if not specified)");
      System.out.println("stopFile:              file with list of stop words");
      return;
    }
    
    String inputDirOrFileList = args[0];
    String corpusName = args[1];
    boolean addFiles = args[2].equals("true");
    String indexDir = args[3];
    String stopFile = args[4];
    
    Set<String> docIds = null;
    if (args.length == 6) {
      System.out.println("Using document id list: " + args[5]);
      docIds = new HashSet<String>();
      BufferedReader br = new BufferedReader(new FileReader(args[5]));
      for (String id; (id = br.readLine()) != null;) {
        docIds.add(id);
      }
    }

    List<File> inputFiles = new ArrayList<File>();

    File inFileSource = new File(inputDirOrFileList);
    if (inFileSource.isDirectory()) {
      System.out.println("generating list of files...");
      Queue<File> dirs = new LinkedList<File>();
      dirs.add(inFileSource);
      while (!dirs.isEmpty()) {
        for (File f : dirs.poll().listFiles()) {
          if (f.isDirectory()) {
            dirs.add(f);
          } else if (f.isFile()) {// && f.getName().toLowerCase().endsWith(".sgm")) {
            inputFiles.add(f);
          } /*else if (f.isFile() && !f.getName().toLowerCase().endsWith(".sgm")) {
            System.out.println("Unexpected filename: " + f.getName());
          }*/
        }
      }
      System.out.println("...done!");
    } else {
      System.out.println("reading list of files...");
      BufferedReader br = new BufferedReader(new FileReader(inFileSource));
      for (String fn; (fn = br.readLine()) != null;) {
        inputFiles.add(new File(fn));
      }
      System.out.println("...done!");
      br.close();
    }


    // Lucene directory for index.
    BufferedReader br = new BufferedReader(new FileReader(stopFile));
    Set<String> stopWords = new HashSet<String>();
    for(String line; (line = br.readLine()) != null;){
	for(String stop:line.split(",")){
	    stopWords.add(stop);
	}
    }
    Directory directory = FSDirectory.open(new File(indexDir));
    IndexWriter writer = new IndexWriter(directory, 
					 new AccentInsensitiveAnalyzer(Version.LUCENE_29, stopWords),
					 IndexWriter.MaxFieldLength.UNLIMITED);
    
    if (!addFiles) {
      System.out.println("clearing index...");
      writer.deleteAll();
      System.out.println("...done!");
    }
    int numDocsIndexed = 0;
    int numFilesIndexed = 0;
    System.out.println("Starting indexing now!");
    for (File inputFile : inputFiles) {
      numFilesIndexed++;
      for (CorpusDocument doc : CorpusReader.readDocuments(
          inputFile.getCanonicalPath())) {

        if (docIds == null || docIds.contains(doc.getId())) {
          org.apache.lucene.document.Document luceneDoc =
              new org.apache.lucene.document.Document();
          // Index and store document id.
          luceneDoc.add(
              new Field(Indexing.ID, doc.getId(), Field.Store.YES,
                  Field.Index.NOT_ANALYZED));
          // Index and store corpus name.
          luceneDoc.add(
              new Field(Indexing.CORPUS, corpusName, Field.Store.YES,
                  Field.Index.NOT_ANALYZED));
          // Index and store filename containing this document.
          luceneDoc.add(
              new Field(Indexing.FILENAME, inputFile.getCanonicalPath(),
                  Field.Store.YES, Field.Index.NOT_ANALYZED));
          // Analyze and index document content, don't store it.
          luceneDoc.add(new Field(Indexing.CONTENTS,
              doc.getBody(),
              Field.Store.NO, Field.Index.ANALYZED));
          writer.addDocument(luceneDoc);
          numDocsIndexed += 1;
        }
        System.out.print("\rindexing... read " + numFilesIndexed + "/" 
            + inputFiles.size() + " files. Indexed " + numDocsIndexed + 
            " documents.");
      }
    }
    System.out.println("...done!");
    System.out.println("optimizing index...");
    writer.optimize();
    System.out.println("...done!");
    System.out.println("Newly indexed documents: " + numDocsIndexed);
    System.out.println("Size of index: " + writer.numDocs());
    writer.close();
  }
}
