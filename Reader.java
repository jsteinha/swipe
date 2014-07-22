import java.util.*;
import java.io.*;
import fig.basic.LogInfo;
public class Reader {
  public static ArrayList<Example> getExamples(String filename) throws Exception {
    Scanner s = new Scanner(new File(filename));
    ArrayList<Example> examples = new ArrayList<Example>();
    while(s.hasNextLine()){
      try {
        String target = s.next(), source = s.next();
        Example ex = new Example(source, target);
        // LogInfo.logs(ex);
        examples.add(ex);
      } catch(Exception e){}
    }
    return examples;
  }

  public static HashMap<String, Double> getDict(String filename) throws Exception {
    Scanner s = new Scanner(new File(filename));
    HashMap<String, Double> ret = new HashMap<String, Double>();
    while(s.hasNextLine()){
      try {
        String word = s.next(), count = s.next();
        ret.put(word, Double.parseDouble(count));
      } catch(Exception e){}
    }
    return ret;
  }
}
