import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;

import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import cc.mallet.fst.*;
import cc.mallet.types.Alphabet;
import cc.mallet.types.AugmentableFeatureVector;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import cc.mallet.types.Sequence;

import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.LineGroupIterator;

import cc.mallet.util.CommandOption;
import cc.mallet.util.MalletLogger;

/**
 * This class's main method trains, tests, or runs a generic CRF-based
 * sequence tagger.
 * <p>
 * Training and test files consist of blocks of lines, one block for each instance, 
 * separated by blank lines. Each block of lines should have the first form 
 * specified for the input of {@link TaggerSentence2FeatureVectorSequence}. 
 * A variety of command line options control the operation of the main program, as
 * described in the comments for {@link #main main}.
 *
 * @author Fernando Pereira <a href="mailto:pereira@cis.upenn.edu">pereira@cis.upenn.edu</a>
 * @version 1.0
 */
public class Tagger
{
    private static Logger logger =
            MalletLogger.getLogger(Tagger.class.getName());

    /**
     * No <code>Tagger</code> objects allowed.
     */
    private Tagger()
    {
    }

    /**
     * Converts an external encoding of a sequence of elements with binary
     * features to a {@link FeatureVectorSequence}.  If target processing
     * is on (training or labeled test data), it extracts element labels
     * from the external encoding to create a target {@link LabelSequence}.
     * Two external encodings are supported:
     * <ol>
     *  <li> A {@link String} containing lines of whitespace-separated tokens.</li>
     *  <li> a {@link String}<code>[][]</code>.</li>
     * </ol>
     *
     * Both represent rows of tokens. When target processing is on, the last token
     * in each row is the label of the sequence element represented by
     * this row. All other tokens in the row, or all tokens in the row if
     * not target processing, are the names of features that are on for
     * the sequence element described by the row.
     *
     */
    public static class TaggerSentence2FeatureVectorSequence extends Pipe
    {
        // gdruck
        // Previously, there was no serialVersionUID.  This is ID that would
        // have been automatically generated by the compiler.  Therefore,
        // other changes should not break serialization.
        private static final long serialVersionUID = -2059308802200728625L;

        /**
         * Creates a new
         * <code>TaggerSentence2FeatureVectorSequence</code> instance.
         */
        public TaggerSentence2FeatureVectorSequence ()
        {
            super (new Alphabet(), new LabelAlphabet());
        }

        /**
         * Parses a string representing a sequence of rows of tokens into an
         * array of arrays of tokens.
         *
         * @param sentence a <code>String</code>
         * @return the corresponding array of arrays of tokens.
         */
        private String[][] parseSentence(String sentence)
        {
            String[] lines = sentence.split("\n");
            String[][] tokens = new String[lines.length][];
            for (int i = 0; i < lines.length; i++)
                tokens[i] = lines[i].split(" ");
            return tokens;
        }

        public Instance pipe (Instance carrier)
        {
            Object inputData = carrier.getData();
            Alphabet features = getDataAlphabet();
            LabelAlphabet labels;
            LabelSequence target = null;
            String [][] tokens;
            if (inputData instanceof String)
                tokens = parseSentence((String)inputData);
            else if (inputData instanceof String[][])
                tokens = (String[][])inputData;
            else
                throw new IllegalArgumentException("Not a String or String[][]; got "+inputData);
            FeatureVector[] fvs = new FeatureVector[tokens.length];
            if (isTargetProcessing())
            {
                labels = (LabelAlphabet)getTargetAlphabet();
                target = new LabelSequence (labels, tokens.length);
            }
            for (int l = 0; l < tokens.length; l++) {
                int nFeatures;
                if (isTargetProcessing())
                {
                    if (tokens[l].length < 1)
                        throw new IllegalStateException ("Missing label at line " + l + " instance "+carrier.getName ());
                    nFeatures = tokens[l].length - 1;
                    target.add(tokens[l][nFeatures]);
                }
                else nFeatures = tokens[l].length;
                ArrayList<Integer> featureIndices = new ArrayList<Integer>();
                for (int f = 0; f < nFeatures; f++) {
                    int featureIndex = features.lookupIndex(tokens[l][f]);
                    // gdruck
                    // If the data alphabet's growth is stopped, featureIndex
                    // will be -1.  Ignore these features.
                    if (featureIndex >= 0) {
                        featureIndices.add(featureIndex);
                    }
                }
                int[] featureIndicesArr = new int[featureIndices.size()];
                for (int index = 0; index < featureIndices.size(); index++) {
                    featureIndicesArr[index] = featureIndices.get(index);
                }
                fvs[l] = featureInductionOption.value ? new AugmentableFeatureVector(features, featureIndicesArr, null, featureIndicesArr.length) :
                        new FeatureVector(features, featureIndicesArr);
            }
            carrier.setData(new FeatureVectorSequence(fvs));
            if (isTargetProcessing())
                carrier.setTarget(target);
            else
                carrier.setTarget(new LabelSequence(getTargetAlphabet()));
            return carrier;
        }
    }

    private static final CommandOption.Double gaussianVarianceOption = new CommandOption.Double
            (Tagger.class, "gaussian-variance", "DECIMAL", true, 10.0,
                    "The gaussian prior variance used for training.", null);

    private static final CommandOption.Boolean trainOption = new CommandOption.Boolean
            (Tagger.class, "train", "true|false", true, false,
                    "Whether to train", null);

    private static final CommandOption.String testOption = new CommandOption.String
            (Tagger.class, "test", "lab or seg=start-1.continue-1,...,start-n.continue-n",
                    true, null,
                    "Test measuring labeling or segmentation (start-i, continue-i) accuracy", null);

    private static final CommandOption.File modelOption = new CommandOption.File
            (Tagger.class, "model-file", "FILENAME", true, null,
                    "The filename for reading (train/run) or saving (train) the model.", null);

    private static final CommandOption.Double trainingFractionOption = new CommandOption.Double
            (Tagger.class, "training-proportion", "DECIMAL", true, 0.5,
                    "Fraction of data to use for training in a random split.", null);

    private static final CommandOption.Integer randomSeedOption = new CommandOption.Integer
            (Tagger.class, "random-seed", "INTEGER", true, 0,
                    "The random seed for randomly selecting a proportion of the instance list for training", null);

    private static final CommandOption.IntegerArray ordersOption = new CommandOption.IntegerArray
            (Tagger.class, "orders", "COMMA-SEP-DECIMALS", true, new int[]{1},
                    "List of label Markov orders (main and backoff) ", null);

    private static final CommandOption.String forbiddenOption = new CommandOption.String(
            Tagger.class, "forbidden", "REGEXP", true,
            "\\s", "label1,label2 transition forbidden if it matches this", null);

    private static final CommandOption.String allowedOption = new CommandOption.String(
            Tagger.class, "allowed", "REGEXP", true,
            ".*", "label1,label2 transition allowed only if it matches this", null);

    private static final CommandOption.String defaultOption = new CommandOption.String(
            Tagger.class, "default-label", "STRING", true, "O",
            "Label for initial context and uninteresting tokens", null);

    private static final CommandOption.Integer iterationsOption = new CommandOption.Integer(
            Tagger.class, "iterations", "INTEGER", true, 500,
            "Number of training iterations", null);

    private static final CommandOption.Boolean viterbiOutputOption = new CommandOption.Boolean(
            Tagger.class, "viterbi-output", "true|false", true, false,
            "Print Viterbi periodically during training", null);

    private static final CommandOption.Boolean connectedOption = new CommandOption.Boolean(
            Tagger.class, "fully-connected", "true|false", true, true,
            "Include all allowed transitions, even those not in training data", null);

    private static final CommandOption.String weightsOption = new CommandOption.String(
            Tagger.class, "weights", "sparse|some-dense|dense", true, "some-dense",
            "Use sparse, some dense (using a heuristic), or dense features on transitions.", null);

    private static final CommandOption.Boolean continueTrainingOption = new CommandOption.Boolean(
            Tagger.class, "continue-training", "true|false", false, false,
            "Continue training from model specified by --model-file", null);

    private static final CommandOption.Integer nBestOption = new CommandOption.Integer(
            Tagger.class, "n-best", "INTEGER", true, 1,
            "How many answers to output", null);

    private static final CommandOption.Integer cacheSizeOption = new CommandOption.Integer(
            Tagger.class, "cache-size", "INTEGER", true, 100000,
            "How much state information to memoize in n-best decoding", null);

    private static final CommandOption.Boolean includeInputOption = new CommandOption.Boolean(
            Tagger.class, "include-input", "true|false", true, false,
            "Whether to include the input features when printing decoding output", null);

    private static final CommandOption.Boolean featureInductionOption = new CommandOption.Boolean(
            Tagger.class, "feature-induction", "true|false", true, false,
            "Whether to perform feature induction during training", null);

    private static final CommandOption.Integer numThreads = new CommandOption.Integer(
            Tagger.class, "threads", "INTEGER", true, 1,
            "Number of threads to use for CRF training.", null);

    private static final CommandOption.List commandOptions =
            new CommandOption.List (
                    "Training, testing and running a generic tagger.",
                    new CommandOption[] {
                            gaussianVarianceOption,
                            trainOption,
                            iterationsOption,
                            testOption,
                            trainingFractionOption,
                            modelOption,
                            randomSeedOption,
                            ordersOption,
                            forbiddenOption,
                            allowedOption,
                            defaultOption,
                            viterbiOutputOption,
                            connectedOption,
                            weightsOption,
                            continueTrainingOption,
                            nBestOption,
                            cacheSizeOption,
                            includeInputOption,
                            featureInductionOption,
                            numThreads
                    });

    /**
     * Create and train a CRF model from the given training data,
     * optionally testing it on the given test data.
     *
     * @param training training data
     * @param testing test data (possibly <code>null</code>)
     * @param eval accuracy evaluator (possibly <code>null</code>)
     * @param orders label Markov orders (main and backoff)
     * @param defaultLabel default label
     * @param forbidden regular expression specifying impossible label
     * transitions <em>current</em><code>,</code><em>next</em>
     * (<code>null</code> indicates no forbidden transitions)
     * @param allowed regular expression specifying allowed label transitions
     * (<code>null</code> indicates everything is allowed that is not forbidden)
     * @param connected whether to include even transitions not
     * occurring in the training data.
     * @param iterations number of training iterations
     * @param var Gaussian prior variance
     * @return the trained model
     */
    public static CRF train(InstanceList training, InstanceList testing,
                            TransducerEvaluator eval, int[] orders,
                            String defaultLabel,
                            String forbidden, String allowed,
                            boolean connected, int iterations, double var, CRF crf)
    {
        Pattern forbiddenPat = Pattern.compile(forbidden);
        Pattern allowedPat = Pattern.compile(allowed);
        if (crf == null) {
            crf = new CRF(training.getPipe(), (Pipe)null);
            String startName =
                    crf.addOrderNStates(training, orders, null,
                            defaultLabel, forbiddenPat, allowedPat,
                            connected);
            for (int i = 0; i < crf.numStates(); i++)
                crf.getState(i).setInitialWeight (Transducer.IMPOSSIBLE_WEIGHT);
            crf.getState(startName).setInitialWeight(0.0);
        }
        logger.info("Training on " + training.size() + " instances");
        if (testing != null)
            logger.info("Testing on " + testing.size() + " instances");

        assert(numThreads.value > 0);
        if (numThreads.value > 1) {
            CRFTrainerByThreadedLabelLikelihood crft = new CRFTrainerByThreadedLabelLikelihood (crf,numThreads.value);
            crft.setGaussianPriorVariance(var);

            if (weightsOption.value.equals("dense")) {
                crft.setUseSparseWeights(false);
                crft.setUseSomeUnsupportedTrick(false);
            }
            else if (weightsOption.value.equals("some-dense")) {
                crft.setUseSparseWeights(true);
                crft.setUseSomeUnsupportedTrick(true);
            }
            else if (weightsOption.value.equals("sparse")) {
                crft.setUseSparseWeights(true);
                crft.setUseSomeUnsupportedTrick(false);
            }
            else {
                throw new RuntimeException("Unknown weights option: " + weightsOption.value);
            }

            if (featureInductionOption.value) {
                throw new IllegalArgumentException("Multi-threaded feature induction is not yet supported.");
            } else {
                boolean converged;
                for (int i = 1; i <= iterations; i++) {
                    converged = crft.train (training, 1);
                    if (i % 1 == 0 && eval != null) // Change the 1 to higher integer to evaluate less often
                        eval.evaluate(crft);
                    if (viterbiOutputOption.value && i % 10 == 0)
                        new ViterbiWriter("", new InstanceList[] {training, testing}, new String[] {"training", "testing"}).evaluate(crft);
                    if (converged)
                        break;
                }
            }
            crft.shutdown();
        }
        else {
            CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood (crf);
            crft.setGaussianPriorVariance(var);

            if (weightsOption.value.equals("dense")) {
                crft.setUseSparseWeights(false);
                crft.setUseSomeUnsupportedTrick(false);
            }
            else if (weightsOption.value.equals("some-dense")) {
                crft.setUseSparseWeights(true);
                crft.setUseSomeUnsupportedTrick(true);
            }
            else if (weightsOption.value.equals("sparse")) {
                crft.setUseSparseWeights(true);
                crft.setUseSomeUnsupportedTrick(false);
            }
            else {
                throw new RuntimeException("Unknown weights option: " + weightsOption.value);
            }

            if (featureInductionOption.value) {
                crft.trainWithFeatureInduction(training, null, testing, eval, iterations, 10, 20, 500, 0.5, false, null);
            } else {
                boolean converged;
                for (int i = 1; i <= iterations; i++) {
                    converged = crft.train (training, 1);
                    if (i % 1 == 0 && eval != null) // Change the 1 to higher integer to evaluate less often
                        eval.evaluate(crft);
                    if (viterbiOutputOption.value && i % 10 == 0)
                        new ViterbiWriter("", new InstanceList[] {training, testing}, new String[] {"training", "testing"}).evaluate(crft);
                    if (converged)
                        break;
                }
            }
        }



        return crf;
    }

    /**
     * Test a transducer on the given test data, evaluating accuracy
     * with the given evaluator
     *
     * @param model a <code>Transducer</code>
     * @param eval accuracy evaluator
     * @param testing test data
     */
    public static void test(TransducerTrainer tt, TransducerEvaluator eval,
                            InstanceList testing)
    {
        eval.evaluateInstanceList(tt, testing, "Testing");
    }

    /**
     * Apply a transducer to an input sequence to produce the k highest-scoring
     * output sequences.
     *
     * @param model the <code>Transducer</code>
     * @param input the input sequence
     * @param k the number of answers to return
     * @return array of the k highest-scoring output sequences
     */
    public static Sequence[] apply(Transducer model, Sequence input, int k)
    {
        Sequence[] answers;
        if (k == 1) {
            answers = new Sequence[1];
            answers[0] = model.transduce (input);
        }
        else {
            MaxLatticeDefault lattice =
                    new MaxLatticeDefault (model, input, null, cacheSizeOption.value());

            answers = lattice.bestOutputSequences(k).toArray(new Sequence[0]);
        }
        return answers;
    }

    public static void main (String[] args) throws Exception
    {
        Reader trainingFile = null, testFile = null;
        InstanceList trainingData = null, testData = null;
        int numEvaluations = 0;
        int iterationsBetweenEvals = 16;
        int restArgs = commandOptions.processOptions(args);
        trainingFile = new FileReader(new File(args[0]));
        testFile = new FileReader(new File(args[1]));

        Pipe p = null;
        CRF crf = null;
        TransducerEvaluator eval = null;

        p = new TaggerSentence2FeatureVectorSequence();
        p.getTargetAlphabet().lookupIndex(defaultOption.value);
//Train
        p.setTargetProcessing(true);
        trainingData = new InstanceList(p);
        trainingData.addThruPipe(
                new LineGroupIterator(trainingFile,
                        Pattern.compile("^\\s*$"), true));
        logger.info
                ("Number of features in training data: "+p.getDataAlphabet().size());
//Test
        testData = new InstanceList(p);
        testData.addThruPipe(
                new LineGroupIterator(testFile,
                        Pattern.compile("^\\s*$"), true));

        logger.info ("Number of predicates: "+p.getDataAlphabet().size());


        if (p.isTargetProcessing())
        {
            Alphabet targets = p.getTargetAlphabet();
            StringBuffer buf = new StringBuffer("Labels:");
            for (int i = 0; i < targets.size(); i++)
                buf.append(" ").append(targets.lookupObject(i).toString());
            logger.info(buf.toString());
        }

        crf = train(trainingData, testData, eval,
                ordersOption.value, defaultOption.value,
                forbiddenOption.value, allowedOption.value,
                connectedOption.value, iterationsOption.value,
                gaussianVarianceOption.value, crf);
        ObjectOutputStream ss =
                new ObjectOutputStream(new FileOutputStream(args[2]));
        ss.writeObject(crf);
        ss.close();

    }
}




/////////////////////////////////////////////////////////////
/* if(args.length != 2)
{
    System.out.println("Name tagger");
    System.out.println("Usage:");
    System.out.println("args[0] training data");
    System.out.println("args[1] input file");
    System.exit(-1);

}
File trainingFile = new File(args[0]);
File inputFile = new File(args[1]);

Tagger.startTagging(trainingFile, inputFile);
*/


/*
int lineCount = 0;
String tkn;
try {
   BufferedReader in = new BufferedReader(new FileReader("/Users/sinanasa/anlp/name_data/test_nwire"));
   String str = null;
   while ((str = in.readLine()) != null) {
       StringTokenizer st = new StringTokenizer(str);
       if (st.hasMoreTokens()) {
           tkn = st.nextToken();
           if (tkn != null) {
               System.out.println(tkn);
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
}     */


/*
          Tagger myTagger = new Tagger();

        //Create an instanceLists from train and test data
        //ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
        //pipeList.add(new Target2Label());
        //pipeList.add(new CharSequence2TokenSequence());
        //pipeList.add(new TokenSequence2FeatureSequence());
        //pipeList.add(new FeatureSequence2FeatureVector());



        ArrayList pipeList = new ArrayList();

        // Read data from File objects
        pipeList.add(new Input2CharSequence("UTF-8"));

        // Regular expression for what constitutes a token.
        //  This pattern includes Unicode letters, Unicode numbers,
        //   and the underscore character. Alternatives:
        //    "\\S+"   (anything not whitespace)
        //    "\\w+"    ( A-Z, a-z, 0-9, _ )
        //    "[\\p{L}\\p{N}_]+|[\\p{P}]+"   (a group of only letters and numbers OR
        //                                    a group of only punctuation marks)
        Pattern tokenPattern =
                Pattern.compile("[\\p{L}\\p{N}_]+");

        // Tokenize raw strings
        pipeList.add(new CharSequence2TokenSequence(tokenPattern));

        // Normalize all tokens to all lowercase
        pipeList.add(new TokenSequenceLowercase());

        // Remove stopwords from a standard English stoplist.
        //  options: [case sensitive] [mark deletions]
        pipeList.add(new TokenSequenceRemoveStopwords(false, false));

        // Rather than storing tokens as strings, convert
        //  them to integers by looking them up in an alphabet.
        pipeList.add(new TokenSequence2FeatureSequence());

        // Do the same thing for the "target" field:
        //  convert a class label string to a Label object,
        //  which has an index in a Label alphabet.
        pipeList.add(new Target2Label());

        // Now convert the sequence of features to a sparse vector,
        //  mapping feature IDs to counts.
        pipeList.add(new FeatureSequence2FeatureVector());

        // Print out the features and the label
        pipeList.add(new PrintInputAndTarget());


        InstanceList trainingInstances = new InstanceList(new SerialPipes(pipeList));
        trainingInstances.addThruPipe(new FileIterator("/Users/sinanasa/anlp/name_data/train_nwire_input"));

        InstanceList testingInstances = new InstanceList(new SerialPipes(pipeList));
        testingInstances.addThruPipe(new FileIterator("/Users/sinanasa/anlp/name_data/test_nwire_input"));

        myTagger.run(trainingInstances, testingInstances);

*/