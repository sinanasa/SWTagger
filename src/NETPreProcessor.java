import java.io.*;
import java.util.StringTokenizer;

/**
 * Created with IntelliJ IDEA.
 * User: sinanasa
 * Date: 9/29/12
 * Time: 8:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class NETPreProcessor {

    private NETPreProcessor() {
    }

    public static void main (String[] args) throws Exception
    {
        int lineCount = 0;
        String tkn;
        try {
            BufferedReader in = new BufferedReader(new FileReader("/Users/sinanasa/anlp/name_data/train_nwire2"));
            // Create file
            FileWriter fstream = new FileWriter("/Users/sinanasa/anlp/name_data/train_nwire2_cap");
            BufferedWriter out = new BufferedWriter(fstream);
            String str = null;
            while ((str = in.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(str);
                if (st.hasMoreTokens()) {
                    tkn = st.nextToken();
                    if (tkn != null) {
                        System.out.println(tkn);
                        if (Character.isUpperCase(tkn.charAt(0))) out.write("CAPITALIZED ");
                        else out.write("lowercase ");
                        if (st.hasMoreTokens()) tkn = st.nextToken();
                        out.write(tkn+"\n");
                    }
                }
                else {
                    System.out.println("");
                    out.write("\n");
                }
                lineCount++;
            }
            in.close();
            out.close();
            System.out.println(lineCount);
        } catch (IOException e) {
    }
}
}
