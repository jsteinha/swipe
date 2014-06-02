import java.util.*;
public class Alignment {
  int len = 0;
  char[] source;
  char[] target;
  boolean[] begin;
  public Alignment(String theSource){
    if(Main.useSpaces){
      len = 2 * theSource.length() + 1;
    } else {
      len = theSource.length();
    }
    source = new char[len];
    target = new char[len];
    begin = new boolean[len];
    for(int i = 0; i < len; i++)
      source[i] = target[i] = '#';
    for(int i = 0; i < theSource.length(); i++){
      int index = Main.useSpaces ? 2*i+1 : i;
      source[index] = theSource.charAt(i);
      target[index] = target[index] = source[index];
      begin[index] = true;
    }
  }
  public String toString(){
    String ret = "";
    for(int i = 0; i < len; i++)
      ret += "-" + source[i] + "- ";
    ret += "\n";
    for(int i = 0; i < len; i++){
      ret += c2s(target[i], begin[i]) + " ";
    }
    return ret;
  }

  public String collapse(){
    String ret = "";
    for(int i = 0; i < len; i++){
      if(begin[i]){
        ret += target[i];
      }
    }
    return ret;
  }

  int editDistance(String answer){
    // Goal: figure out how many edits would need to be made to get from 
    // current state to the answer.
    // An edit is a modification of a character, and potentially 
    // to whether we're starting a new character
    // State: current prefix of answer, current prefix of sourse
    int len2 = answer.length();
    int[][] dp = new int[len+1][len2+1];
    for(int i = 0; i <= len; i++){
      for(int j = 0; j <= len2; j++){
        dp[i][j] = 99999;
      }
    }
    dp[0][0] = 0;
    for(int i = 0; i <= len; i++){
      for(int j = 0; j <= len2; j++){
        if(i < len && target[i] == '#')
          dp[i+1][j] = Math.min(dp[i+1][j], dp[i][j]);
        if(i < len)
          dp[i+1][j] = Math.min(dp[i+1][j], dp[i][j] + 1);

        // TODO this rule is a bit hacky
        if(i < len && j < len2 && target[i] == answer.charAt(j)){
            //&& (j == 0 || answer.chatAt(j) != answer.chatAt(j-1) 
            //           || begin[i]))
          dp[i+1][j+1] = Math.min(dp[i+1][j+1], dp[i][j]);
          dp[i+1][j] = Math.min(dp[i+1][j], dp[i][j]);
        }
        if(i < len && j < len2)
          dp[i+1][j+1] = Math.min(dp[i+1][j+1], dp[i][j] + 1);
      }
    }
    if(answer.equals(collapse())) return 0;
    else return 1 + dp[len][len2];
  }

  double score(HashMap<String, Double> features){
    double ret = 0.0;
    for(Map.Entry<String,Double> entry : features.entrySet()){
      Double incr = Main.params.get(entry.getKey());
      if(incr != null){
        ret += incr * entry.getValue();
      }
    }
    return ret;
  }

  public Map<String, Double> propose(int i){
    // propose a change to character i
    // if next value is I-c, change automatically to B-c if we move away from c
    // if prev value is I-c, can be either B-c or I-c
    HashMap<String, Double> logprobs = new HashMap<String, Double>();
    HashMap<String, HashMap<String,Double>> features = 
      new HashMap<String, HashMap<String,Double>>();
    HashMap<String, Double> curFeatures;
    boolean old_begin = i+1 < len && begin[i+1];
    String str;
    for(char c = 'a'; c <= 'z'; c++){
      // B-c
      if(i+1 < len) begin[i+1] = old_begin;
      str = c2s(c, true);
      if(i+1 < len && target[i+1] != c && target[i+1] != '#') begin[i+1] = true;
      target[i] = c;
      begin[i] = true;
      curFeatures = extractFeatures();
      features.put(str, curFeatures);
      logprobs.put(str, score(curFeatures));
      if(i > 0 && target[i-1] == c){ // I-c
        if(i+1 < len) begin[i+1] = old_begin;
        str = c2s(c, false);
        if(i+1 < len && target[i+1] != c && target[i+1] != '#') begin[i+1] = true;
        target[i] = c;
        begin[i] = false;
        curFeatures = extractFeatures();
        features.put(str, curFeatures);
        logprobs.put(str, score(curFeatures));
      }
    }
    
    str = c2s('#', false);
    if(i+1 < len && target[i+1] != '#') begin[i+1] = true;
    target[i] = '#';
    begin[i] = false;
    curFeatures = extractFeatures();
    features.put(str, curFeatures);
    logprobs.put(str, score(curFeatures));
    
    HashMap<String, Double> gradient = new HashMap<String, Double>();
    Util.logNormalize(logprobs);
    for(String _str : logprobs.keySet()){
      Util.update(gradient, features.get(_str), -Math.exp(logprobs.get(_str)));
    }
    str = Util.sample(logprobs);
    Util.update(gradient, features.get(str), 1.0);
    if(i+1 < len) begin[i+1] = old_begin;
    char c; boolean b;
    if(str.equals("OO")){ c = '#'; b = false; }
    else if(str.charAt(0) == 'B'){ c = str.charAt(1); b = true; }
    else { c = str.charAt(1); b = false; }
    target[i] = c;
    begin[i] = b;
    if(i+1 < len && target[i+1] != c && target[i+1] != '#') begin[i+1] = true;
    return gradient;
  }

  static String c2s(char c, boolean begin){
    if(c == ' ' || c == '#') return "OO";
    else if(begin) return "B" + c;
    else return "I" + c;
  }
  String src_tar(String cur_src, String cur_tar){
    return "cr_sr,cr_tr:" + cur_src + ":"+cur_tar;
  }
  String src_tar_prevT1(String cur_src, String cur_tar, String prev_tar){
    return "cr_sr,cr_tr,pr_tr,1:"+cur_src+":"+cur_tar+":"+prev_tar;
  }
  String src_tar_prevT2(String cur_src, String cur_tar, String prev_tar){
    return "cr_sr,cr_tr,pr_tr,2:"+cur_src+":"+cur_tar+":"+prev_tar;
  }
  String src_tar_prevS(String cur_src, String cur_tar, String prev_src){
    return "cr_sr,cr_tr,pr_sr:"+cur_src+":"+cur_tar+":"+prev_src;
  }

  HashMap<String, Double> extractFeatures(){
    HashMap<String, Double> ret = new HashMap<String, Double>();
    for(int i = 0; i < len; i++){
      String cur_src = "" + source[i],
             cur_tar = c2s(target[i], begin[i]);
      Util.update(ret, src_tar(cur_src, cur_tar));
      String prev;
      if(i > 0){
        prev = c2s(target[i-1], begin[i-1]);
        Util.update(ret,src_tar_prevT1(cur_src, cur_tar, prev));
        prev = ""+source[i-1];
        Util.update(ret,src_tar_prevS(cur_src, cur_tar, prev));
      }
      int j = i;
      while(j >= 0 && !begin[j]) j--;
      j--;
      while(j >= 0 && target[j] == '#') j--;
      if(j >= 0){
        prev = ""+target[j];
        Util.update(ret,src_tar_prevT2(cur_src, cur_tar, prev));
      }
    }
    String answer = collapse();
    Double freq = Main.dictionary.get(answer);
    if(freq != null){
      Util.update(ret,"YD");
      Util.update(ret,"FD",Math.log(freq));
    }
    else {
      Util.update(ret,"ND");
    }
    for(int i = 0; i < answer.length(); i++){
      for(int j = i+1; j <= answer.length(); j++){
        freq = Main.dictionary.get(answer.substring(i,j));
        if(freq != null){
          Util.update(ret,"YP"+(j-i));
          Util.update(ret,"FP"+(j-i),Math.log(freq));
        }
        else {
          Util.update(ret,"NP"+(j-i));
        }
      }
    }
    return ret;
  }

  static void copyFeatures(){
    ArrayList<String> keys = new ArrayList<String>(Main.params.keySet());
    for(String key : keys){
      if(!key.startsWith("init-"))
        throw new RuntimeException("this should never happen: " + key);
      Main.params.put(key.substring(5), Main.params.get(key));
    }
  }

  HashMap<String, Double> extractFeaturesSimple(){
    HashMap<String, Double> ret = new HashMap<String,Double>();
    for(int i = 0; i < len; i++){
      Util.update(ret,"init-"+src_tar(""+source[i],c2s(target[i],begin[i])));
      if(i>0){
        Util.update(ret,"init-"+src_tar_prevS(""+source[i],
                                      c2s(target[i],begin[i]),
                                      ""+source[i-1]));
        Util.update(ret,"init-"+src_tar_prevT1(""+source[i],
                                       c2s(target[i],begin[i]),
                                       c2s(target[i-1],begin[i-1])));
      }
    }
    return ret;
  }

  HashMap<String,Double> simpleInit(){ // initializes alignment according to simple model
    ArrayList<String> alphabet = new ArrayList<String>();
    for(char c = 'a'; c <= 'z'; c++){
      alphabet.add(c2s(c,false));
      alphabet.add(c2s(c,true));
    }
    alphabet.add(c2s('#',false));
    int S = alphabet.size();
    double[][] nodes = new double[len][S];
    for(int i = 0; i < len; i++){
      for(int j = 0; j < S; j++){
        nodes[i][j] += Util.getSafe(Main.params, 
                        "init-"+src_tar(""+source[i],alphabet.get(j)));
        if(i>0){
          nodes[i][j] += Util.getSafe(Main.params,
                          "init-"+src_tar_prevS(""+source[i],
                                                alphabet.get(j),
                                                ""+source[i-1]));
        }
      }
    }
    double[][][] edges = new double[len][S][S];
    for(int i=1;i<len;i++){
      for(int j=0;j<S;j++){
        for(int k=0;k<S;k++){
          // gives probability of k -> j
          edges[i][j][k] += Util.getSafe(Main.params,
            "init-"+src_tar_prevT1(""+source[i],alphabet.get(j),alphabet.get(k)));
        }
      }
    }
    double[][] marginals = new double[len][S];
    for(int j = 0; j < S; j++){
      marginals[0][j] = nodes[0][j];
    }
    for(int i=1;i<len;i++){
      for(int j=0;j<S;j++){
        marginals[i][j] = Double.NEGATIVE_INFINITY;
        for(int k=0;k<S;k++){
          marginals[i][j] = Util.lse(marginals[i][j],
                                     marginals[i-1][k] + edges[i][j][k]);
        }
      }
    }
    int index = Util.sample(marginals[len-1]);
    target[len-1] = targetOf(alphabet.get(index));
    begin[len-1] = beginOf(alphabet.get(index));
    for(int i=len-2;i>=0;i--){
      for(int j = 0; j < S; j++){
        marginals[i][j] += edges[i+1][index][j];
      }
      index = Util.sample(marginals[i]);
      target[i] = targetOf(alphabet.get(index));
      begin[i] = beginOf(alphabet.get(index));
    }
    return extractFeaturesSimple();
  }
  char targetOf(String x){
    if(x.equals("OO")) return '#';
    else return x.charAt(1);
  }
  boolean beginOf(String x){
    return x.charAt(0) == 'B';
  }
}

