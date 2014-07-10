import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.OptionsParser;
import fig.basic.StatFig;
import fig.exec.Execution;
import fig.record.Record;
public class Main implements Runnable {
  @Option(gloss="experiment name (for easier tracking)")
  public static String experimentName = "UNKNOWN";
  @Option(gloss="MCMC steps", required=true)
  public static int T;
  @Option(gloss="seond-stage MCMC steps", required=false)
  public static int T2 = 50;
  @Option(gloss="MCMC burn-in", required=true)
  public static int B;
  @Option(gloss="number of passes", required=true)
  public static int Q;
  @Option(gloss="number of random restarts", required=true)
  public static int K;
  @Option(gloss="step size", required=true)
  public static double eta;
  @Option(gloss="inference", required=true)
  public static String inference;
  @Option(gloss="learning", required=true)
  public static String learning;
  @Option(gloss="verbosity")
  public static int verbosity = 0;
  @Option(gloss="number of rounds of smart initialization")
  public static int Q2 = 0;
  @Option(gloss="number of rounds of intermediate transitions")
  public static int Q1 = 0;
  @Option(gloss="number of samples for smart initialization")
  public static int K2 = 0;
  @Option(gloss="use spaces for padding")
  public static boolean useSpaces = true;
  @Option(gloss="file for dataset")
  public static String dataset = "train2.dat";
  @Option(gloss="word counts")
  public static String dict_file = "counts.dat";
  @Option(gloss="partial counts")
  public static String partial_dict_file = "partial_counts.dat";
  @Option(gloss="number of training examples")
  public static int numTrain = 24000;
  @Option(gloss="number of test examples")
  public static int numTest = 1000;
  @Option(gloss="how frequently to print out summary statistics of test accuracy")
  public static int testFrequency = 6000;
  @Option(gloss="number of threads to use")
  public static int numThreads = 5;
  


  //static HashMap<String, Double> params = new HashMap<String, Double>();
  static HashMap<String, Double> G1 = new HashMap<String, Double>(),
                                 G2 = new HashMap<String, Double>(),
                                 params = new HashMap<String, Double>();
  static HashMap<String, Double> dictionary, partialDict;
  static StatFig score = new StatFig(),
                 edits = new StatFig(),
                 correct = new StatFig();

  void adagrad(Map<String, Double> gradient){
    for(Map.Entry<String, Double> entry: gradient.entrySet()){
      String key = entry.getKey();
      double value = entry.getValue();
      Util.update(G1, key, value);
      Util.update(G2, key, value * value);
      params.put(key, eta * G1.get(key) / Math.sqrt(1e-4 + G2.get(key)));
    }
  }

  void adagrad2(Map<String, Double> gradient){
    for(Map.Entry<String, Double> entry: gradient.entrySet()){
      String key = entry.getKey();
      double value = entry.getValue();
      //Util.update(G1, key, value);
      Util.update(G2, key, value * value);
      Util.update(params, key, eta * value / Math.sqrt(1e-4 + G2.get(key)));
    }
  }

  void sgd(Map<String, Double> gradient){
    for(Map.Entry<String, Double> entry : gradient.entrySet()){
      Util.update(params, entry.getKey(), eta * entry.getValue());
    }
  }

  void updateParams(Map<String, Double> gradient){
    if(verbosity >= 2){
      LogInfo.begin_track("Printing gradient...");
      Util.printMap(gradient);
      LogInfo.end_track();
    }
    if(learning.equals("sgd")) sgd(gradient);
    else if(learning.equals("adagrad")) adagrad(gradient);
    else if(learning.equals("adagrad2")) adagrad2(gradient);
    else throw new RuntimeException("invalid learning algorithm: " + learning);
  }

  void dumpStats(String name){
    LogInfo.begin_track("dumping stats...");
    LogInfo.logs("%s score: %s",   name, score);
    LogInfo.logs("%s edits: %s",   name, edits);
    LogInfo.logs("%s correct: %s", name, correct);
    LogInfo.end_track();
    Record.add(String.format("%s score", name), score);
    Record.add(String.format("%s edits", name), edits);
    Record.add(String.format("%s correct", name), correct);
    Record.flush();
    score = new StatFig();
    edits = new StatFig();
    correct = new StatFig();
  }

  public void run() {
    try {
      runWithException();
    } catch(Exception e) {
			e.printStackTrace();
      throw new RuntimeException();
    }
  }

  void runWithException() throws Exception {
    final String inference = Main.inference;
    ArrayList<Example> examples = Reader.getExamples(dataset);
    numTrain = Math.min(numTrain, examples.size() - numTest);
    if(numTrain % testFrequency != 0){
      throw new RuntimeException("test frequency must divide training size");
    }
    dictionary = Reader.getDict(dict_file);
    partialDict = Reader.getDict(partial_dict_file);
    Record.add("sources", dataset, dict_file, partial_dict_file);
    Record.add("learning", learning, eta, Q2, K2);
    Record.add("inference", inference, T, B, K, useSpaces);
    Record.add("sizes", numTrain, numTest, testFrequency);
    Record.flush();

    for(int q = 0; q < Q2; q++){
      LogInfo.begin_track("Beginning pre-iteration %d", q);
      for(int i = 0; i < numTrain; i++){
        Example ex = examples.get(i);
        LogInfo.begin_track("Example: %s", ex);
        updateParams(ComputeGradient.gradientUU(ex));
        LogInfo.end_track();
      }
      dumpStats("init");
      LogInfo.end_track();
    }
    Alignment.copyFeatures("init-", "A-");
    for(int q = 0; q < Q1; q++) {
      LogInfo.begin_track("Beginning intermediate-transition %d", q);
      for(int i = 0; i < numTrain; i++) {
        Example ex = examples.get(i);
        LogInfo.begin_track("Example: %s", ex);
        updateParams(ComputeGradient.gradientUA(ex));
        LogInfo.end_track();
      }
    }
    Alignment.copyFeatures("A-", "B-");
    for(int q = 0; q < Q; q++){
      LogInfo.begin_track("Beginning iteration %d", q);
      for(int i = 0; i < numTrain; i++){
        Example ex = examples.get(i);
        LogInfo.logs("Example: %s", ex);
        if(inference.equals("UA")){
          updateParams(ComputeGradient.gradientUA(ex));
        }else if(inference.equals("UAB")) {
          updateParams(ComputeGradient.gradientUAB(ex));
        }else if(inference.equals("AA")){
          updateParams(ComputeGradient.gradientAA(ex));
        } else {
          throw new RuntimeException("invalid inference algorithm: " + inference);
        }
        if((i+1) % testFrequency == 0){
          dumpStats("train");
          final ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
          for(int i2 = numTrain; i2 < numTrain + numTest; i2++){
            final Example ex2 = examples.get(i2);
            threadPool.submit(new Runnable() {
              @Override
              public void run() {
                try {
                  if(inference.equals("UA"))
                    ComputeGradient.gradientUA(ex2, false);
                  else if(inference.equals("UAB"))
                    ComputeGradient.gradientUAB(ex2, false);
                  else if(inference.equals("AA"))
                    ComputeGradient.gradientAA(ex2, false);
                } catch(Exception e){
                  e.printStackTrace();
                  throw new RuntimeException();
                }
              }
            });
          }
          threadPool.shutdown();
          threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
          dumpStats("test");
        }
      }
      if(verbosity >= 1){
        LogInfo.begin_track("Printing params...");
        Util.printMap(params);
        LogInfo.end_track();
      }
      LogInfo.end_track();
    }
  }


  public static void main(String[] args){
    Execution.run(args, new Main());
  }

}

class Example {
  String source, target;
  public Example(String source, String target){
    this.source = source;
    this.target = target;
  }
  @Override
  public String toString(){
    return source + " => " + target;
  }

}

