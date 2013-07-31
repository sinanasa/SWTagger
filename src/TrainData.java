import java.io.*;
import java.util.*;
import java.io.IOException;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.tagger.maxent.TaggerConfig;


/**
 * Created with IntelliJ IDEA.
 * User: sinanasa
 * Date: 10/1/12
 * Time: 8:50 PM
 * To change this template use File | Settings | File Templates.
 */
public class TrainData {

    public Vector nameVector;
    private String[] featureVector;
    public Vector labelVector;


    private class LabelElement {
        private int begin=0;
        private int end=0;
        private LabelElement() {
        }
        public void setBegin(int i) { begin = i;}
        public void setEnd(int i) { end = i;}
        public int getBegin() {return begin;}
        public int getEnd() {return end;}
    }

    private class LabelData {
        public String name;
        public Set label;
    }


    public TrainData(BufferedReader in) throws IOException {
        nameVector = new Vector();
        labelVector = new Vector();
        featureVector = new String[1000000];
        String line=null;
        String token=null;
        while ((line = in.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line);
            if (st.hasMoreTokens()) {
                nameVector.add(st.nextToken());
                //tokens between first and last go into features vector
                while (st.hasMoreTokens()) {
                    token = st.nextToken();
                }
                //last token goes into label vector
                labelVector.add(token);
            }
            else {
                nameVector.add("");
                labelVector.add("");
            }
        }
    }

    public HashMap labelSet = null;

    public void createLabelSet(Vector nameList) {
        labelSet = new HashMap();
        String lbl = null;
        String name = null;
        Vector v;
        int j = 0;
        while (j<nameList.size()) {
            lbl = (String)nameList.get(j);
            if (lbl.startsWith("B-")) {
                LabelElement element = new LabelElement();
                element.setBegin(j);
                while (((String)nameList.get(j+1)).startsWith("I-")) j++;
                element.setEnd(j);
                name = lbl.substring(2);
                if (labelSet.get(name) == null) {
                    v = new Vector();
                    labelSet.put(name, v);
                }
                else {
                    v = (Vector)labelSet.get(name);
                }
                v.add(element);
            }
            j++;
        }
    }

    public void printLabelSet() {
        System.out.println("PER");
        Iterator it = ((Vector)(labelSet.get("PER"))).iterator();
        while (it.hasNext()) {
            LabelElement pair = (LabelElement)it.next();
            if (pair.getBegin() != pair.getEnd()) {
                for (int i = pair.getBegin(); i <= pair.getEnd(); i++ ) {
                   System.out.print(nameVector.get(i) + " ");
                }
                System.out.println();
            }
        }
    }

    public void addGazetteerFeature() {
        System.out.println("GPE");
        Iterator it = ((Vector)(labelSet.get("GPE"))).iterator();
        while (it.hasNext()) {
            LabelElement pair = (LabelElement)it.next();
            if (pair.getBegin() != pair.getEnd()) {
                for (int i = pair.getBegin(); i <= pair.getEnd(); i++ ) {
                    //System.out.print(nameVector.get(i) + " ");
                    featureVector[i] = new String("GAZET");
                }
                //System.out.println();
            }
        }
    }

    public void printFeatures() {
        System.out.println("Features");
        int it = 0;
        while (it < featureVector.length) {
            if (featureVector[it] == null) {
                //System.out.println("UNK");
            }
            else {
                System.out.print(nameVector.get(it) + " ");
                System.out.println((String) featureVector[it]);
            }
            it++;
        }
    }


    public void posTag() throws ClassNotFoundException, IOException {
        /////////////////////
        // Initialize the tagger
        Properties prop = new Properties();
        prop.load((InputStream)new FileInputStream("/Users/sinanasa/anlp/stanford-postagger/models/wsj-0-18-left3words.tagger.props"));
        TaggerConfig tc = new TaggerConfig(prop);
        MaxentTagger tagger = new MaxentTagger("/Users/sinanasa/anlp/stanford-postagger/models/wsj-0-18-left3words.tagger", tc);

        FileWriter fstream = new FileWriter("/Users/sinanasa/anlp/name_data/test_nwire_pos");
        BufferedWriter out = new BufferedWriter(fstream);

        String tagged = new String();
        String sentence = new String();
        String token = new String();
        //tagger.tagTokenizedString(nameVector.toString());
        String tokenName = new String();

        Iterator it = (Iterator) nameVector.iterator();
        while (it.hasNext()) {
            token = (String)it.next();
            while (!token.equals("")) {
                sentence = sentence + token + " ";
                token = (String)it.next();
            }
            // The tagged string
            tagged = tagger.tagString(sentence);

            // Output the result
            //System.out.println(sentence);
            //System.out.println(tagged);

            StringTokenizer stName = new StringTokenizer(sentence);
            //Tokenize the tagged and print tags
            StringTokenizer st = new StringTokenizer(tagged);
            while (st.hasMoreTokens()) {
                token = st.nextToken();
                tokenName = stName.nextToken();
                //System.out.println(tokenName + " " + token);
                out.write(token.substring(0, token.indexOf("~")) + " ");
                out.write(token.substring(token.indexOf("~")+1, token.length()) + "\n");
            }
            out.write("\n");
            sentence = "";
        }
        out.close();
    }


    public static void main (String[] args) throws Exception {
        TrainData td = new TrainData(new BufferedReader(new FileReader("/Users/sinanasa/anlp/name_data/test_nwire")));
        for (Object o : td.nameVector) {
            System.out.println(o);
        }
        td.createLabelSet(td.labelVector);
        ////td.printLabelSet();
        //td.addGazetteerFeature();
        //td.printFeatures();

/*
        Iterator it = (Iterator) td.nameVector.iterator();
        while (it.hasNext()) {
            System.out.println((String)it.next());
        }
        System.out.println("BITTIIIIII");
*/

        td.posTag();
        System.out.println("Size of namespace: " + td.nameVector.size());
    }

}
