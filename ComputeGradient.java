import java.util.*;
import fig.basic.LogInfo;
import java.util.concurrent.*;

public class ComputeGradient {
  public static Map<String, Double> gradientUU(Example ex){
    return gradientUU(ex, true);
  }
  public static Map<String, Double> gradientUU(Example ex, boolean train){
    int K2 = Main.K2;
    Map<String, Double> gradient = !train? null : new HashMap<String, Double>();
    ArrayList<HashMap<String,Double> > features = 
      new ArrayList<HashMap<String,Double> >();
    ArrayList<Double> logWeights = new ArrayList<Double>();
    double correct = 0.0, edits = 0.0, score = 0.0;
    Alignment a = new Alignment(ex.source);
    for(int k = 0; k < K2; k++){
      features.add(a.simpleInit(k != 0));
      int dist = a.editDistance(ex.target);
      logWeights.add(-1.0 * dist);  
      score += Math.exp(-dist) / K2;
      edits += dist / (1.0 * K2 * a.len);
      if(a.collapse().equals(ex.target)) correct += 1.0/K2;
      // LogInfo.logs("final sample: %s", a.collapse());
    }
    // LogInfo.logs("score: %f, edit fraction: %f, correct fraction: %f", score, edits, correct);
    Main.score.add(score);
    Main.edits.add(edits);
    Main.correct.add(correct);
    if(!train) return null;
    Util.logNormalize(logWeights);
    for(int k = 0; k < K2; k++){
      Util.update(gradient, 
                  features.get(k), 
                  Math.exp(logWeights.get(k)) - 1.0/K2);
    }
    return gradient;
  }

   public static Map<String, Double> gradientUA(Example ex) throws Exception {
    return gradientUA(ex, true);
  }
  public static Map<String, Double> gradientUA(final Example ex, boolean train) throws Exception {
    final int T = Main.T, B = Main.B, K = train ? Main.K : 1;
    double correct = 0.0;
    double allT = 0.0;
    Triple result = new Triple();
    ArrayList<Future<Triple>> samplers = new ArrayList<Future<Triple>>();
    final ExecutorService threadPool = Executors.newFixedThreadPool(Main.numThreads);
    for(int k = 0; k < K; k++){
      Callable<Triple> sampler = new Callable<Triple>(){
        public Triple call() throws Exception {
          Triple triple = new Triple();
          double correct = 0.0;
          int T1 = B;
          while(Math.random() * (T-B) > 1.0) T1++;
          final Alignment a = new Alignment(ex.source);
          triple.gradients.add(a.simpleInit());
          triple.logWeights.add(Double.NEGATIVE_INFINITY);
          triple.initial.add(true);
          for(int t = 0; t < T1; t++){
            triple.gradients.add(a.propose((int)(Math.random() * a.len), 
                                            new Alignment.FeatureExtract() {
                                              public HashMap<String, Double> run() {
                                                return a.extractFeatures();
                                              }
                                            }));
            if(t >= B){
              triple.logWeights.add(-1.0 * a.editDistance(ex.target));
              if(a.collapse().equals(ex.target)) correct += 1.0/(K*(T1-B));
            } else {
              triple.logWeights.add(Double.NEGATIVE_INFINITY);
            }
            triple.initial.add(false);
          }
          LogInfo.logs("final sample: %s", a.collapse());
          triple.correct = correct;
          triple.effT = T1;
          return triple;
        }
      };
      if(train) {
        samplers.add(threadPool.submit(sampler));
      } else {
        Triple ret = sampler.call();
        ret.appendTo(result);
        correct += ret.correct;
        allT += ret.effT;
      }
    }
    threadPool.shutdown();
    threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    if(train){
      for(Future<Triple> sampler : samplers){
        Triple ret = sampler.get();
        ret.appendTo(result);
        correct += ret.correct;
        allT += ret.effT;
      }
    }
    double score = 0.0, edits = 0.0;
    int len = new Alignment(ex.source).len;
    for(int i = 0; i < result.logWeights.size(); i++){
      score += Math.exp(result.logWeights.get(i)) / (K*(T-B));
      if(result.logWeights.get(i) > Double.NEGATIVE_INFINITY){
        edits += -result.logWeights.get(i) / (K * (T-B) * len);
      }
    }
    LogInfo.logs("score: %f, edit fraction: %f, correct fraction: %f", score, edits, correct);
    Main.score.add(score);
    Main.edits.add(edits);
    Main.correct.add(correct);
    Main.infertime.add(allT/(double)K);
    if(!train) return null;
    Util.logNormalize(result.logWeights);
    Map<String, Double> answer = new HashMap<String, Double>();
    double cumulativeWeight = 0.0;
    for(int i = result.logWeights.size() - 1; i >= 0; i--){
      cumulativeWeight += Math.exp(result.logWeights.get(i));
      Util.update(answer, result.gradients.get(i), cumulativeWeight);
      if(result.initial.get(i)) cumulativeWeight = 0.0;
    }
    return answer;
  }

  public static Map<String, Double> gradientUAa(Example ex) throws Exception {
    return gradientUAa(ex, true);
  }
  public static Map<String, Double> gradientUAa(final Example ex, boolean train) throws Exception {
    final int T = Main.T, B = Main.B, K = train ? Main.K : 1;
    final HashMap<String, Double> params = Main.params;
    double correct = 0.0;
    Triple result = new Triple();
    ArrayList<Future<Triple>> samplers = new ArrayList<Future<Triple>>();
    final ExecutorService threadPool = Executors.newFixedThreadPool(Main.numThreads);
    if(B > T) 
      throw new Exception("B ("+B+") is not supposed to be bigger than T("+T+").");
    LogInfo.logs("lambda-dic: %f, lambda-time: %f", Main.params.get("lambda-dic"), Main.params.get("lambda-time"));
    for(int k = 0; k < K; k++){
      Callable<Triple> sampler = new Callable<Triple>(){
        public Triple call() throws Exception {
          double lambda_dic = params.get("lambda-dic"), lambda_time = params.get("lambda-time");
          Triple triple = new Triple();
          double correct = 0.0;
          final Alignment a = new Alignment(ex.source);
          triple.gradients.add(a.simpleInit());
          triple.gradientsStop.add(null);
          triple.finalSample.add(a.collapse());
          triple.edits.add(0);
          triple.logWeights.add(Double.NEGATIVE_INFINITY);
          triple.initial.add(true);
          for(int t = 0;; t++){
            // compute the gradients. 
            triple.gradients.add(a.propose((int)(Math.random() * a.len), 
                                            new Alignment.FeatureExtract() {
                                              public HashMap<String, Double> run() {
                                                return a.extractFeatures();
                                              }
                                            }));
            triple.finalSample.add(a.collapse());
            int feat_indic = Main.dictionary.get(a.collapse()) == null ? 0 : 1;
            double feat_time = (double)ex.source.length();
            double prob = Util.sigmoid(lambda_dic*feat_indic+lambda_time*feat_time);
            HashMap<String, Double> g = new HashMap<String, Double>();
            g.put("lambda-dic-pos", (1-prob)*feat_indic);
            g.put("lambda-dic-neg", -prob*feat_indic);
            g.put("lambda-time-pos", (1-prob)*feat_time);
            g.put("lambda-time-neg", -prob*feat_time);
            // compute the importance weights. 
            triple.initial.add(false);
            if(t >= B){
              triple.gradientsStop.add(g);
              triple.logWeights.add(-1.0 * a.editDistance(ex.target)+Math.log(prob)-Main.c*Math.max(0, t-T));
              triple.edits.add(a.editDistance(ex.target));
              if(a.collapse().equals(ex.target)) correct += 1.0;
              if(Math.random() < prob) { // stop.
                triple.effT = t+1;
                break;
              }
            } else {
              triple.gradientsStop.add(g);
              triple.edits.add(0);
              triple.logWeights.add(Double.NEGATIVE_INFINITY);
            }
          }
          triple.correct = correct/(double)(K*(triple.effT-B));
          return triple;
        }
      };
      samplers.add(threadPool.submit(sampler));
    }
    threadPool.shutdown();
    threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    int allT = 0;
    for(Future<Triple> sampler : samplers){
      Triple ret = sampler.get();
      LogInfo.begin_track("sampler "+sampler.toString());
      for(String sample : ret.finalSample) {
        LogInfo.logs("target: %s, final sample: %s", ex.target, sample);
      }
      LogInfo.end_track();
      allT += ret.effT;
      ret.appendTo(result);
      correct += ret.correct;
    }
    Main.records.put("T", (double)allT/K);

    double score = 0.0, edits = 0.0;
    int len = new Alignment(ex.source).len;

    for(int i = 0; i < result.logWeights.size(); i++){
      score += Math.exp(result.logWeights.get(i)) / (double)allT;
      if(result.logWeights.get(i) > Double.NEGATIVE_INFINITY){
        edits += result.edits.get(i) / (allT * len);
      }
    }
    LogInfo.logs("score: %f, edit fraction: %f, correct fraction: %f, average T: %f", 
                                        score, edits, correct, allT/(double)K);
    Main.score.add(score);
    Main.edits.add(edits);
    Main.correct.add(correct);
    Main.infertime.add(allT/(double)K);
    if(!train) return null;
    Util.logNormalize(result.logWeights);
    Map<String, Double> answer = new HashMap<String, Double>();
    double cumulativeWeight = 0.0;
    for(int i = result.logWeights.size() - 1; i >= 0; i--){
      double weight = Math.exp(result.logWeights.get(i));
      if(result.gradientsStop.get(i) != null) {
        Util.update(answer, "lambda-dic", result.gradientsStop.get(i).get("lambda-dic-pos")*weight
                                          +result.gradientsStop.get(i).get("lambda-dic-neg")*cumulativeWeight);
        Util.update(answer, "lambda-time", result.gradientsStop.get(i).get("lambda-time-pos")*weight
                                          +result.gradientsStop.get(i).get("lambda-time-neg")*cumulativeWeight);
      }
      cumulativeWeight += weight;
      Util.update(answer, result.gradients.get(i), cumulativeWeight);
      if(result.initial.get(i)) cumulativeWeight = 0.0;
    }
    return answer;
  }

  public static Map<String, Double> gradientUAB(Example ex) throws Exception {
    return gradientUAB(ex, true);
  }

  // deprecated. in construction. 
  public static Map<String, Double> gradientUAB(final Example ex, boolean train) throws Exception {
    final int T = Main.T, T2 = Main.T2, B = Main.B, K = train ? Main.K : 1, Tstar = Main.Tstar; 
    double exlen = ex.source.length();
    double lambda1 = Main.params.get("lambda1"), lambda2 = Main.params.get("lambda2");

    double correct = 0.0;
    Triple result = new Triple();
    ArrayList<Future<Triple>> samplers = new ArrayList<Future<Triple>>();
    final ExecutorService threadPool = Executors.newFixedThreadPool(Main.numThreads);

    for(int k = 0; k < K; k++){
      Callable<Triple> sampler = new Callable<Triple>(){
        public Triple call() throws Exception {
          Triple triple = new Triple();
          double correct = 0.0;
          // sample transition. 
          int t1 = 1;
          while(Math.random() * T > 1.0) t1++;
          int t2 = 0;
          while(Math.random() * T2 > 1.0) t2++;
          // sample particle. 
          final Alignment a = new Alignment(ex.source);
          triple.gradients.add(a.simpleInit());
          triple.logWeights.add(Double.NEGATIVE_INFINITY);
          triple.initial.add(true);
          for(int t = 0; t < B+t1+t2; t++){
            if(t < B+t1) 
              triple.gradients.add(a.propose((int)(Math.random() * a.len), 
                                            new Alignment.FeatureExtract() {
                                              public HashMap<String, Double> run() {
                                                return a.extractFeatures(false, "");
                                              }
                                            }));
            else
              triple.gradients.add(a.propose((int)(Math.random() * a.len), 
                                            new Alignment.FeatureExtract() {
                                              public HashMap<String, Double> run() {
                                                // strategy 1: not sharing parameters.
                                                return a.extractFeatures(true, "last-");
                                                // strategy 2: sharing parameters of second- and third-stage chain.
                                                // return a.extractFeatures(true, "");
                                              }
                                            }));
            if(t >= B){
              triple.logWeights.add(-1.0 * a.editDistance(ex.target));
              if(a.collapse().equals(ex.target)) correct += 1.0/(K*(t1+t2));
            } else {
              triple.logWeights.add(Double.NEGATIVE_INFINITY);
            }
            triple.initial.add(false);
          }
          LogInfo.logs("final sample: %s", a.collapse());
          triple.correct = correct;
          return triple;
        }
      };
      if(train) {
        samplers.add(threadPool.submit(sampler));
      } else {
        Triple ret = sampler.call();
        ret.appendTo(result);
        correct += ret.correct;
      }
    }
    threadPool.shutdown();
    threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    if(train){
      for(Future<Triple> sampler : samplers){
        Triple ret = sampler.get();
        ret.appendTo(result);
        correct += ret.correct;
      }
    }
    double score = 0.0, edits = 0.0;
    int len = new Alignment(ex.source).len;
    for(int i = 0; i < result.logWeights.size(); i++){
      score += Math.exp(result.logWeights.get(i)) / (K*(T+T2+1));
      if(result.logWeights.get(i) > Double.NEGATIVE_INFINITY){
        edits += -result.logWeights.get(i) / (K * (T+T2+1) * len);
      }
    }
    LogInfo.logs("score: %f, edit fraction: %f, correct fraction: %f", score, edits, correct);
    Main.score.add(score);
    Main.edits.add(edits);
    Main.correct.add(correct);
    if(!train) return null;
    Util.logNormalize(result.logWeights);
    Map<String, Double> answer = new HashMap<String, Double>();
    double cumulativeWeight = 0.0;
    for(int i = result.logWeights.size() - 1; i >= 0; i--){
      cumulativeWeight += Math.exp(result.logWeights.get(i));
      Util.update(answer, result.gradients.get(i), cumulativeWeight);
      if(result.initial.get(i)) cumulativeWeight = 0.0;
    }
    return answer;
  }


  public static Map<String, Double> gradientAA(Example ex) throws Exception {
    return gradientAA(ex, true);
  }
  public static Map<String, Double> gradientAA(final Example ex, boolean train) throws Exception {
    final int T = Main.T, B = Main.B, K = train ? Main.K : 1;
    
    ArrayList<Integer> distances = new ArrayList<Integer>();
    ArrayList<HashMap<String,Double> > features = 
        new ArrayList<HashMap<String,Double> >();
    ArrayList<Double> logwt = new ArrayList<Double>();
    ArrayList<Future<Pair>> samplers = new ArrayList<Future<Pair>>();
    final ExecutorService threadPool = Executors.newFixedThreadPool(Main.numThreads);
    double correct = 0.0;
    for(int k = 0; k < K; k++){
      Callable<Pair> sampler = new Callable<Pair>(){
        public Pair call() throws Exception {
          Pair pair = new Pair();
          double correct = 0.0;
          final Alignment a = new Alignment(ex.source);
          for(int t = 0; t < T; t++){
            a.propose((int)(Math.random() * a.len), new Alignment.FeatureExtract() {
                                                    public HashMap<String, Double> run() {
                                                      return a.extractFeatures();
                                                    }
                                                  });
            if(t >= B){
              pair.distances.add(a.editDistance(ex.target));
              pair.features.add(a.extractFeatures());
              if(a.collapse().equals(ex.target)) correct += 1.0/(K*(T-B));
            }
          }
          LogInfo.logs("final sample: %s", a.collapse());
          pair.correct = correct;
          return pair;
        }
      };
      if(train) {
        samplers.add(threadPool.submit(sampler));
      } else {
        Pair ret = sampler.call();
        ret.appendTo(distances, features, logwt);
        correct += ret.correct;
      }
    }
    threadPool.shutdown();
    threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    if(train){
      for(Future<Pair> sampler : samplers){
        Pair ret = sampler.get();
        ret.appendTo(distances, features, logwt);
        correct += ret.correct;
      }
    }
    //double[] logwt = new double[distances.size()];
    //for(int i = 0; i < distances.size(); i++){
    //  logwt[i] = -1.0 * distances.get(i);
    //}
    double score = 0.0;
    double edits = 0.0;
    int len = new Alignment(ex.source).len;
    for(int i = 0; i < distances.size(); i++){
      score += Math.exp(-distances.get(i)) / distances.size();
      edits += distances.get(i) / (1.0 * distances.size() * len);
    }
    LogInfo.logs("score: %f, edit fraction: %f, correct fraction: %f", score, edits, correct);
    Main.score.add(score);
    Main.edits.add(edits);
    Main.correct.add(correct);
    if(!train) return null;
    Util.logNormalize(logwt);
    HashMap<String, Double> gradient = new HashMap<String, Double>();
    for(int i = 0; i < features.size(); i++){
      double alpha = 1.0 * (Math.exp(logwt.get(i)) - 1.0 / distances.size());
      Util.update(gradient, features.get(i), alpha);
    }
    return gradient;
  }
}

class Triple {
  ArrayList<Map<String, Double> > gradients = new ArrayList<Map<String, Double>>();
  ArrayList<Map<String, Double> > gradientsStop = new ArrayList<Map<String, Double> >();
  ArrayList<Double> logWeights = new ArrayList<Double>();
  ArrayList<Boolean> initial = new ArrayList<Boolean>();
  ArrayList<Integer> edits = new ArrayList<Integer>();
  ArrayList<String> finalSample = new ArrayList<String>();
  double correct = 0.0;
  int effT = 0;
  public Triple() {}
  void appendTo(Triple triple){
    triple.gradients.addAll(this.gradients);
    triple.gradientsStop.addAll(this.gradientsStop);
    triple.logWeights.addAll(this.logWeights);
    triple.initial.addAll(this.initial);
    triple.edits.addAll(this.edits);
    triple.finalSample.addAll(this.finalSample);
  }
}

class Pair {
  ArrayList<Integer> distances = new ArrayList<Integer>();
  ArrayList<HashMap<String,Double> > features = 
      new ArrayList<HashMap<String,Double> >();
  double correct = 0.0;
  public Pair() {}
  void appendTo(ArrayList<Integer> distances,
                ArrayList<HashMap<String,Double> > features,
                ArrayList<Double> logwt){
    distances.addAll(this.distances);
    features.addAll(this.features);
    ArrayList<Double> logwt2 = new ArrayList<Double>();
    for(int i = 0; i < this.distances.size(); i++){
      logwt2.add(-1.0 * distances.get(i));
    }
    Util.logNormalize(logwt2);
    logwt.addAll(logwt2);
  }
}
