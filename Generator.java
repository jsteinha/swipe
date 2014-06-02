import java.util.*;
import java.io.*;
public class Generator {
  public static String[] rows = new String[]{
    "qwertyuiop",
    "asdfghjkl",
    "zxcvbnm"
  };
  static String alphabet = rows[0] + rows[1] + rows[2];
  static double[] center_x = new double[256],
           center_y = new double[256];
  // keys extend 0.5 to the left and right of each of their centers

  static char keyAt(double x, double y){
    for(int i = 0; i < alphabet.length(); i++){
      int p = (int)alphabet.charAt(i);
      if(x >= center_x[p] - 0.5 && x < center_x[p] + 0.5 && y >= center_y[p] - 0.5 && y < center_y[p] + 0.5) return (char)p;
    }
    return '#';
  }

  static boolean isAlpha(String name) {
    return name.matches("[a-zA-Z]+");
  }
  static HashMap<String, Double> dict = new HashMap<String, Double>(),
                                 partialDict = new HashMap<String, Double>();
  public static void main(String[] args) throws Exception {
    for(int i = 0; i < rows.length; i++){
      for(int j = 0; j < rows[i].length(); j++){
        center_x[(int)rows[i].charAt(j)] = 0.4 * i + j + 0.5;
        center_y[(int)rows[i].charAt(j)] = i + 0.5;
      }
    }
    // for(int t = 0; t < 100; t++){
    //   System.out.print(keyAt(10*Math.random(), 3*Math.random()));
    // }
    // System.out.println();

    // Scanner s = new Scanner(new File("nyt_small"));
    // ArrayList<String> words = new ArrayList<String>();
    // while(s.hasNext()){
    //   String tok = s.next();
    //   if(!isAlpha(tok)) continue;
    //   Util.update(dict, tok, 1.0);
    //   for(int i = 0; i < tok.length(); i++){
    //     for(int j = i+1; j <= tok.length(); j++){
    //       Util.update(partialDict, tok.substring(i, j), 1.0);
    //     }
    //   }
    //   //words.add(tok);
    //   //if(Math.random() < .0001) System.out.println(tok);
    // }
    // for(String key : partialDict.keySet()){
    //   System.out.println(key + " " + partialDict.get(key));
    // }
    // // Collections.shuffle(words);
    // // for(int i = 0; i < 25000; i++){
    // //   System.out.println(words.get(i));
    // // }

    Scanner s = new Scanner(new File("1word.dat"));
    while(s.hasNext()){
      String source = s.next();
      String target = swipe(source);
      System.out.println(source + " " + target);
    }
  }

  static String swipe(String source){
    int len = source.length();
    double[] xs = new double[len],
             ys = new double[len];
    // first, build the points
    for(int i = 0; i < len; i++){
      double x, y;
      int p = (int)source.charAt(i);
      do {
        x = center_x[p] + 1.15 * (Math.random() - 0.5);
        y = center_y[p] + 1.15 * (Math.random() - 0.5);
      } while(keyAt(x,y) == '#');
      xs[i] = x;
      ys[i] = y;
    }
    // second, build a sequence of times
    // goes as follows:
    // 0.3 on each character
    // 0.7 away from each character
    int total = 2 * len + (int)(Math.random() * len);
    double[] pts = new double[total];
    for(int i = 0; i < total; i++)
      pts[i] = Math.random() * (0.3 * len + 0.7 * (len-1));
    Arrays.sort(pts);
    char[] out = new char[total];
    for(int i = 0; i < total; i++){
      int index = (int)pts[i];
      double remainder = pts[i] - index;
      double xp, yp;
      if(remainder < 0.3) { xp = xs[index]; yp = ys[index]; } //out[i] = keyAt(xs[index], ys[index]);
      else { xp = xs[index] + (remainder - 0.3) * (xs[index+1] - xs[index]) / 0.7;
             yp = ys[index] + (remainder - 0.3) * (ys[index+1] - ys[index]) / 0.7; }
           //out[i] = keyAt(xs[index] + (remainder - 0.3) * (xs[index+1] - xs[index]) / 0.7,
           //               ys[index] + (remainder - 0.3) * (ys[index+1] - ys[index]) / 0.7);
      out[i] = keyAt(xp, yp);
      System.out.print(out[i] + "("+xp+","+yp+") ");
    }
    System.out.println();
    return new String(out);
  }
}
