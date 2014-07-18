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
    final double c = Main.c;

    if(Main.params.get("lambda") >= 0) { // projection.
      Main.params.put("lambda", -0.1);
    }

    double correct = 0.0;
    Triple result = new Triple();
    ArrayList<Future<Triple>> samplers = new ArrayList<Future<Triple>>();
    final ExecutorService threadPool = Executors.newFixedThreadPool(Main.numThreads);
    if(B > T) 
      throw new Exception("B ("+B+") is not supposed to be bigger than T("+T+").");
    for(int k = 0; k < K; k++){
      Callable<Triple> sampler = new Callable<Triple>(){
        public Triple call() throws Exception {
          double lambda = 0;
          try{
            lambda = Main.params.get("lambda");
          }catch(Exception ee) {;}
          Triple triple = new Triple();
          double correct = 0.0;
          int T1 = 0;
          if(lambda >= 0)
            throw new Exception("lambda should not be >= 0.");
          double ratio = lambda/(double)ex.source.length();
          while(Math.log(Math.random()) <= ratio) T1++;
          final Alignment a = new Alignment(ex.source);
          triple.gradients.add(a.simpleInit());
          triple.gradientsTime.add(0.0);
          triple.logWeightsTime.add(Double.NEGATIVE_INFINITY);
          triple.logWeights.add(Double.NEGATIVE_INFINITY);
          triple.initial.add(true);
          triple.effT = T1;
          for(int t = 0; t < B+T1; t++){
            triple.gradients.add(a.propose((int)(Math.random() * a.len), 
                                            new Alignment.FeatureExtract() {
                                              public HashMap<String, Double> run() {
                                                return a.extractFeatures();
                                              }
                                            }));
            if(t >= B){
              triple.gradientsTime.add((t-B)/(double)ex.source.length());
              double pweight = -2*c*Math.max(0, t-T)-Math.log(1-Math.exp(ratio))
                                          +ratio*(T-B)-Math.log(1-Math.exp(ratio-c));
              triple.logWeightsTime.add(pweight);
              triple.logWeights.add(-1.0 * a.editDistance(ex.target)+pweight);
              if(a.collapse().equals(ex.target)) correct += 1.0/(K*(T1-B));
            } else {
              triple.gradientsTime.add(0.0);
              triple.logWeights.add(Double.NEGATIVE_INFINITY);
              triple.logWeightsTime.add(Double.NEGATIVE_INFINITY);
            }
            triple.initial.add(false);
          }
          // LogInfo.logs("final sample: %s", a.collapse());
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
    int allT = 0;
    if(train){
      for(Future<Triple> sampler : samplers){
        Triple ret = sampler.get();
        allT += ret.effT;
        ret.appendTo(result);
        correct += ret.correct;
      }
    }
    double score = 0.0, edits = 0.0;
    int len = new Alignment(ex.source).len;
    for(int i = 0; i < result.logWeights.size(); i++){
      score += Math.exp(result.logWeights.get(i)) / allT;
      if(result.logWeights.get(i) > Double.NEGATIVE_INFINITY){
        edits += -(result.logWeights.get(i)-result.logWeightsTime.get(i)) / (allT * len);
      }
    }
    // LogInfo.logs("score: %f, edit fraction: %f, correct fraction: %f", score, edits, correct);
    Main.score.add(score);
    Main.edits.add(edits);
    Main.correct.add(correct);
    if(!train) return null;
    Util.logNormalize(result.logWeights);
    Util.logNormalize(result.logWeightsTime);
    Map<String, Double> answer = new HashMap<String, Double>();
    double cumulativeWeight = 0.0;
    for(int i = result.logWeights.size() - 1; i >= 0; i--){
      cumulativeWeight += Math.exp(result.logWeights.get(i));
      Util.update(answer, "lambda", (Math.exp(result.logWeights.get(i))
                                      -Math.exp(result.logWeightsTime.get(i)))*result.gradientsTime.get(i));
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
    double c = Main.c;
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
          // LogInfo.logs("final sample: %s", a.collapse());
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
    // LogInfo.logs("score: %f, edit fraction: %f, correct fraction: %f", score, edits, correct);
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
  ArrayList<Double> gradientsTime = new ArrayList<Double>();
  ArrayList<Double> logWeights = new ArrayList<Double>();
  ArrayList<Double> logWeightsTime = new ArrayList<Double>();
  ArrayList<Boolean> initial = new ArrayList<Boolean>();
  double correct = 0.0;
  int effT = 0;
  public Triple() {}
  void appendTo(Triple triple){
    triple.gradients.addAll(this.gradients);
    triple.gradientsTime.addAll(this.gradientsTime);
    triple.logWeights.addAll(this.logWeights);
    triple.logWeightsTime.addAll(this.logWeightsTime);
    triple.initial.addAll(this.initial);
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
