import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Vector;

/**
 * This class is scorer for name tagging output, please refer to the comment of main function for usage
 * @author Qi
 *
 */
public class Scorer_Independent
{
    public static final String NONE_ENTITY = "None";

    public static class NamedEntity
    {
        public boolean correct; // indicates correct or not

        public Vector<String> entity;
        public String type;

        public NamedEntity(String type)
        {
            this.type = type;
            this.entity = new Vector<String>();
        }

        public void addToken(String token)
        {
            this.entity.add(token);
        }

        @Override
        public String toString()
        {
            String ret = "";
            ret += entity + " ";

            ret += this.type;
            return ret;
        }

        @Override
        public boolean equals(Object obj)
        {
            if(obj instanceof NamedEntity)
            {
                NamedEntity target = (NamedEntity) obj;
                if(target.type.equals(this.type) && this.entity.equals(target.entity))
                {
                    return true;
                }
            }
            return false;
        }

        /**
         * get the total length of the entity
         * @return
         */
        public int length()
        {
            int ret = 0;
            for(String token : entity)
            {
                ret += token.length();
            }
            return ret;
        }
    }

    public static class Scores
    {
        // map from type --> num
        public Map<String, Double> tab_num_correct;
        public Map<String, Double> tab_num_gold;
        public Map<String, Double> tab_num_ans;

        public double num_correct = 0.0;
        public double false_positive = 0.0;
        public double num_missing = 0.0;
        public double num_type_error = 0.0;
        public double num_gold = 0.0;
        public double num_ans = 0.0;

        public int num_docs = 0;

        public Scores()
        {
            tab_num_correct = new HashMap<String, Double>();
            tab_num_gold = new HashMap<String, Double>();
            tab_num_ans = new HashMap<String, Double>();
        }
    }

    public static void getScoreDocument(File goldFile, File ansFile, Scores scores) throws IOException
    {
        List<List<NamedEntity>> entityList_gold = readEntities(goldFile);
        List<List<NamedEntity>> entityList_ans = readEntities(ansFile);
        getScoreDocument(entityList_gold, entityList_ans, scores);
    }

    public static void printScores(File goldFile, File ansFile) throws IOException
    {
        List<List<NamedEntity>> entityList_gold = readEntities(goldFile);
        List<List<NamedEntity>> entityList_ans = readEntities(ansFile);
        Scores scores = new Scores();
        getScoreDocument(entityList_gold, entityList_ans, scores);
        printScores(scores);
    }

    /**
     * evaluate a dir contains gold-standard and predication
     * with line-by-line version
     * @param dir
     * @param suffix_gold: suffix of gold-standard file
     * @param suffix_ne: suffix of predication file
     * @throws IOException
     */
    static public void evaluateDir(File outputDir, File goldDir) throws IOException
    {
        File[] files = outputDir.listFiles();

        Stack<File> children = new Stack<File>();
        children.addAll(Arrays.asList(files));
        Scores total_scores = new Scores();

        while(!children.empty())
        {
            File child = children.pop();
            if(child.isFile() && !child.isHidden())
            {
                File gold_child = new File(goldDir, child.getName());
                getScoreDocument(gold_child, child, total_scores);
            }
            else
            {
                File[] sub_files = child.listFiles();
                children.addAll(Arrays.asList(sub_files));
            }
        }
        printScores(total_scores);
    }

    protected static boolean ignoreThe = true; // weather ignore "The" when comparing gold standard and output

    /**
     * calculate P/R/F scores comparing tow named entity lists,
     * first list is gold-standard, 2nd list is answer
     * @param list_gold
     * @param list_ans
     * @return
     */
    static public void getScoreDocument(List<List<NamedEntity>> list_gold, List<List<NamedEntity>> list_ans, Scores scores)
    {
        scores.num_docs ++;
        scores.num_gold += getNamedEntityNum(list_gold);
        scores.num_ans += getNamedEntityNum(list_ans);

        // count # of gold for different types
        for(List<NamedEntity> sent : list_gold)
        {
            for(NamedEntity token : sent)
            {
                if(!token.type.equals(NONE_ENTITY))
                {
                    String type = token.type;
                    Double temp = scores.tab_num_gold.get(type);
                    if(temp == null)
                    {
                        temp = 0.0;
                    }
                    temp++;
                    scores.tab_num_gold.put(type, temp);
                }
            }
        }

        // count # of ans for different types
        for(List<NamedEntity> sent : list_ans)
        {
            for(NamedEntity token : sent)
            {
                if(!token.type.equals(NONE_ENTITY))
                {
                    String type = token.type;
                    Double temp = scores.tab_num_ans.get(type);
                    if(temp == null)
                    {
                        temp = 0.0;
                    }
                    temp++;
                    scores.tab_num_ans.put(type, temp);
                }
            }
        }

        for(int sent = 0; sent < list_gold.size(); sent++)
        {
            List<NamedEntity> sent_gold = list_gold.get(sent);
            List<NamedEntity> sent_ans = list_ans.get(sent);

            int indx_ans = 0;
            int indx_gold = 0;
            int token_num_ans = 0;
            int token_num_gold = 0;

            // count # of correct
            for(; indx_ans < sent_ans.size(); indx_ans++)
            {
                NamedEntity entity_ans =  sent_ans.get(indx_ans);
                NamedEntity entity_gold = sent_gold.get(indx_gold);

                int next_token_num_ans = token_num_ans + entity_ans.length();
                int next_token_num_gold = token_num_gold + entity_gold.length();

                // while they don't have intersection
                // the right part of the statement is for the special case that entity_ans and entity_gold are empty
                while(! ((token_num_ans < next_token_num_gold && next_token_num_ans > token_num_gold) || (entity_ans.length() == 0 && entity_gold.length() == 0)))
                {
                    entity_gold = sent_gold.get(indx_gold);
                    token_num_gold = token_num_gold + entity_gold.length();
                    indx_gold++;
                    entity_gold = sent_gold.get(indx_gold);
                    next_token_num_gold = token_num_gold + entity_gold.length();
                }

                if(entity_ans.equals(entity_gold) && !entity_gold.type.equals(NONE_ENTITY))
                {
                    scores.num_correct++;

                    String type = entity_gold.type;
                    Double temp = scores.tab_num_correct.get(type);
                    if(temp == null)
                    {
                        temp = 0.0;
                    }
                    temp++;
                    scores.tab_num_correct.put(type, temp);
                }
                else if(ignoreThe && !entity_gold.type.equals(NONE_ENTITY) && entity_gold.type.equals(entity_ans.type))
                {
                    // compare "the United States" with "United States", it doesn't make any difference
                    String str_gold = "";
                    for(int i=0; i<entity_gold.entity.size(); i++)
                    {
                        String word = entity_gold.entity.get(i);
                        if(i==0 && word.equalsIgnoreCase("the"))
                        {
                            continue;
                        }
                        str_gold += word + " ";
                    }
                    String str_ans = "";
                    for(int i=0; i<entity_ans.entity.size(); i++)
                    {
                        String word = entity_ans.entity.get(i);
                        if(i==0 && word.equalsIgnoreCase("the"))
                        {
                            continue;
                        }
                        str_ans += word + " ";
                    }
                    if(str_ans.equals(str_gold))
                    {
                        scores.num_correct++;

                        String type = entity_gold.type;
                        Double temp = scores.tab_num_correct.get(type);
                        if(temp == null)
                        {
                            temp = 0.0;
                        }
                        temp++;
                        scores.tab_num_correct.put(type, temp);
                    }
                }

                token_num_ans += entity_ans.length();
            }
        }

    }

    public static void printScores(Scores scores)
    {
        double precision = scores.num_correct / scores.num_ans;
        double recall = scores.num_correct / scores.num_gold;
        double F_1 = 2 * (precision * recall) / (precision + recall);

        System.out.println("Number of docs: " + scores.num_docs);

        System.out.println("Performance: ");
        System.out.println("Number of correct: " + scores.num_correct);
        System.out.println("Number of answer: " + scores.num_ans);
        System.out.println("Number of gold: " + scores.num_gold);

        System.out.println("Precision: " + precision);
        System.out.println("Recall: " + recall);
        System.out.println("F_1: " + F_1);

        System.out.println("Breakdown performance");
        for(String type : scores.tab_num_ans.keySet())
        {
            Double num_correct = scores.tab_num_correct.get(type);
            if(num_correct == null)
            {
                num_correct = 0.0;
            }
            Double num_gold = scores.tab_num_gold.get(type);
            if(num_correct == null)
            {
                num_gold = 0.0;
            }
            Double num_ans = scores.tab_num_ans.get(type);
            if(num_correct == null)
            {
                num_ans = 0.0;
            }
            precision = num_correct / num_ans;
            recall = num_correct / num_gold;
            F_1 = 2 * (precision * recall) / (precision + recall);
            System.out.println("Type : " + type);
            System.out.println("Number of correct: " + num_correct);
            System.out.println("Number of gold: " + num_gold);
            System.out.println("Number of answer: " + num_ans);
            System.out.println("Precision: " + precision);
            System.out.println("Recall: " + recall);
            System.out.println("F_1: " + F_1);
        }
    }

    /**
     * count number of named entity in a list,
     * don't consider None-entity (labeled as "O")
     * @param list
     * @return
     */
    public static int getNamedEntityNum(List<List<NamedEntity>> list)
    {
        int ret = 0;
        for(List<NamedEntity> sent : list)
        {
            for(NamedEntity entity : sent)
            {
                if(! entity.type.equalsIgnoreCase(NONE_ENTITY))
                {
                    ret ++;
                }
            }
        }
        return ret;
    }

    /**
     * read entities from a file
     * each line is one token, followed by it's BILOU label,
     * e.g.
     * Microsoft U-ORG
     * is O
     * @param input
     * @throws IOException
     */
    static public List<List<NamedEntity>> readEntities(File input) throws IOException
    {
        List<List<NamedEntity>> ret = new ArrayList<List<NamedEntity>>();

        BufferedReader reader = new BufferedReader(new FileReader(input));
        String line = "";

        char pre_label_initial = 'O';
        String entityType = "";
        NamedEntity entity = null;
        int count = 0;
        List<NamedEntity> sent = new ArrayList<NamedEntity>();
        while((line = reader.readLine()) != null)
        {
            count++;
            if(line.matches("^\\s*$"))
            {
                // an empty line
                ret.add(sent);
                sent = new ArrayList<NamedEntity>();
            }
            else
            {
                String[] feilds = line.split("\\s");
                String token = feilds[0];
                String label = feilds[feilds.length-1];

                char labelInitial = label.charAt(0);

                String label_type = "";
                if(label.length() > 2)
                {
                    label_type = label.substring(2, label.length());
                }
                else
                {
                    label_type = NONE_ENTITY;
                }

                switch(labelInitial)
                {
                    case 'B':
                        entityType = label_type;
                        entity = new NamedEntity(entityType);
                        entity.addToken(token);
                        sent.add(entity);
                        break;
                    case 'I':
                        if(!(pre_label_initial == 'B' || pre_label_initial == 'I'))
                        {
                            System.err.println(input.getAbsolutePath() + " Line " + count + " " + "Error: \'I\' follows non-B/I label");
                        }
                        else if(!entityType.equalsIgnoreCase(label_type))
                        {
                            System.err.println(input.getAbsolutePath() + " Line " + count + " " + "Error: \'I-2\' follows B-1 label");
                        }
                        entity.addToken(token);
                        break;
                    case 'O':
                        entityType = label_type;
                        entity = new NamedEntity(entityType);
                        entity.addToken(token);
                        sent.add(entity);
                        break;
                    default:
                        System.err.println(input.getAbsolutePath() + " Line " + count + " " + "Invalid label :" + label);
                        break;
                }

                pre_label_initial = labelInitial;
            }
        }
        reader.close();

        return ret;
    }

    /**
     * Evaluate the performance of tagging output against its gold standard
     * the output and gold standard should have consistent file name
     * args[0] system output dir
     * args[1] gold standard dir
     * @param args
     * @throws IOException
     */
    static public void main(String[] args) throws IOException
    {
        if(args.length != 2)
        {
            System.out.println("Name tagging scorer");
            System.out.println("Usage:");
            System.out.println("args[0] system output dir");
            System.out.println("args[1] gold standard dir");
            System.exit(-1);

        }
        File outputDir = new File(args[0]);
        File goldDir = new File(args[1]);

        Scorer_Independent.evaluateDir(outputDir, goldDir);
    }
}