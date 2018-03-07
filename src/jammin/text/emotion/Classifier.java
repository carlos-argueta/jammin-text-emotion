/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jammin.text.emotion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jblas.DoubleMatrix;
import org.jblas.Geometry;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Carlos
 */
public class Classifier {

    private final TreeMap<String, String> matricesPaths;
    private final TreeMap<String, String> groupsPaths;

    private static final TreeMap<String, DoubleMatrix> matrices = new TreeMap();
    private static TreeMap<String, TreeMap<String, String>> groups = new TreeMap();
    private static TreeMap<String, TreeMap<String, Double>> patterns = new TreeMap();
    private static TreeMap<String, TreeMap<Integer, String>> emotions = new TreeMap();

    private static TreeMap<String, Integer> polarities = new TreeMap();

    private static TreeMap<String, Integer> votesBlank = new TreeMap();
    private static TreeMap<String, String> votesDetailBlank = new TreeMap();
    private TreeMap<String, Integer> votes;
    private TreeMap<String, String> voters;
    private TreeMap<String, String> votesDetail;

    private int maxVotes;
    private int minNonFact;
    private int votesToWin;
    FileOutputStream fstream;
    BufferedWriter out;

    //private EmotionAPI emoApi;
    public Classifier(int mv, int vtw, int mnf) {

        matricesPaths = new TreeMap();
        groupsPaths = new TreeMap();

        matricesPaths.put("en", "/Volumes/Transcend/Dropbox/workspace/jammin-text-emotion/resources/matrices/emotion_matrix_en");
        matricesPaths.put("en_liwc", "/Volumes/Transcend/Dropbox/workspace/jammin-text-emotion/resources/matrices/emotion_matrix_en_liwc");

        groupsPaths.put("en", "/Volumes/Transcend/Dropbox/workspace/jammin-text-emotion/resources/groups/en");
        
        maxVotes = mv;
        minNonFact = mnf;
        votesToWin = vtw;

        loadEmotionMatrices();
        loadEmotionGroups();

        polarities.put("joy", 1);
        polarities.put("sadness", -1);
        polarities.put("trust", 1);
        polarities.put("disgust", -1);
        polarities.put("fear", -1);
        polarities.put("anger", -1);
        polarities.put("anticipation", 0);
        polarities.put("surprise", 0);

    }

   
    public int vote(String vote, String voter) {
        if (voters.containsKey(voter)) {
            return 0;
        }

        voters.put(voter, voter);
        votes.put(vote, votes.get(vote) + 1);
        voter = voter.replaceAll("\\d", "");
        if (voter.contains("terror")) {
            voter = "trust";
        }
        votesDetail.put(vote, votesDetail.get(vote) + "\t" + voter);
        // lastVoted = vote;
        return votes.get(vote);
    }

    class PatternMatchFinder implements Callable<DoubleMatrix> {

        private TreeMap<String, Double> patterns = new TreeMap();
        private String tweet;
        private String name;

        public PatternMatchFinder(String name, TreeMap<String, Double> patts, String t) {
            this.patterns = patts;
            this.tweet = t;
            this.name = name;
        }

        public DoubleMatrix call() {
            Iterator<String> it = patterns.keySet().iterator();
            int n = patterns.size();
            double[] vector = new double[n];
            int index = 0;
            while (it.hasNext()) {
                String pattern = it.next();
                Matcher matcher = Pattern.compile(pattern).matcher(tweet);
                int count = 0;
                while (matcher.find()) {
                    count++;
                }
                vector[index] = count;
                index++;
            }

            // Matrix mult to evaluate
            DoubleMatrix tVector = new DoubleMatrix(n, 1, vector);
            return tVector;
        }

    }

    public String evalBurts(String tweet, String lang) {
        System.out.println("Evaluate "+tweet+" with language "+lang);
        votes = new TreeMap();
        votes.putAll(votesBlank);

        votesDetail = new TreeMap();
        votesDetail.putAll(votesDetailBlank);

        voters = new TreeMap();

        tweet = tweet.trim().toLowerCase();

        String emotion;
        boolean hasBull = false;
        String bull1 = "no";
        String bull2 = "no";
        String isit = "";
        
        final ExecutorService service;
        final Future<DoubleMatrix> task1, task2;

        service = Executors.newFixedThreadPool(2);
        task1 = service.submit(new PatternMatchFinder(lang, patterns.get(lang), tweet));
        task2 = service.submit(new PatternMatchFinder(lang + "_liwc", patterns.get(lang + "_liwc"), tweet));
        int[][] perm1 = null;
        int[][] perm2 = null;
        try {
            final DoubleMatrix tVector1, tVector2;

            tVector1 = task1.get();
            if (matrices.containsKey(lang + "_liwc")) {
                
                tVector2 = task2.get();
            } else {
                
                tVector2 = null;
            }
            

            if (tVector2 != null) {
                DoubleMatrix result2 = matrices.get(lang + "_liwc").mmul(tVector2);
                perm2 = result2.columnSortingPermutations();
            }
            DoubleMatrix result = matrices.get(lang).mmul(tVector1);
            perm1 = result.columnSortingPermutations();
        } catch (final InterruptedException ex) {
            ex.printStackTrace();
        } catch (final ExecutionException ex) {
            ex.printStackTrace();
        }

        service.shutdownNow();

        String resultString = "";
        TreeMap<String, String> noRep = new TreeMap();

        // Voting for patterns based models
        int stopAt = maxVotes;
        for (int j = 0; j < stopAt; j++) {

            emotion = emotions.get(lang).get(perm1[0][j]);
            isit += emotion + "\t";

            if (groups.get(lang).containsKey(emotion)) {
                String[] votes = groups.get(lang).get(emotion).split("\t");
                for (String vote : votes) {

                    vote(vote, emotion);

                }

            } else {
                stopAt++;
            }

        }

        // LIWC
        if (perm2 != null) {
            stopAt = maxVotes;
            for (int j = 0; j < stopAt; j++) {

                emotion = emotions.get(lang + "_liwc").get(perm2[0][j]);
                isit += emotion + "\t";

                if (groups.get(lang).containsKey(emotion)) {
                    String[] votes = groups.get(lang).get(emotion).split("\t");
                    for (String vote : votes) {

                        vote(vote, emotion);

                    }

                } else {
                    stopAt++;
                }

            }
        }

        SortedSet map = entriesSortedByValuesDesc(votes);

        Iterator<Map.Entry> itVotes = map.iterator();
        String rank = "";

        int count = 0;
        int countAmb = 0;
        JSONObject output = new JSONObject();
        output.put("text", tweet);
        output.put("lang", lang);

        JSONArray groups1 = new JSONArray();
        JSONArray groups2 = new JSONArray();
        String amb1 = "no";
        int currPol1 = 0;
        String amb2 = "no";
        int currPol2 = 0;
        while (itVotes.hasNext()) {
            Map.Entry<String, Integer> mood = itVotes.next();
            hasBull = false;
            //Fill guessed
            emotion = mood.getKey();

            String[] toks = votesDetail.get(emotion).split("\t");

            JSONObject group = new JSONObject();

            JSONArray emotions = new JSONArray();

            for (String t : toks) {
                if (!noRep.containsKey(t) && t.length() > 0) {
                    if (t.equals("bullying")) {
                        hasBull = true;
                    }
                    emotions.put(t);
                    resultString += t + " ";
                    noRep.put(t, t);
                }
            }
            if (emotions.length() > 0) {
                //group.put("name", groupTranslations(lang).get(emotion)

                group.put("name", emotion
                );
                group.put("emotions", emotions);
                if (mood.getValue() >= votesToWin) {
                    if (currPol1 != 0 && polarities.get(emotion) != 0 && currPol1 != polarities.get(emotion)) {
                        amb1 = "yes";
                    }
                    if (hasBull && (emotion.equals("anger") || emotion.equals("disgust"))) {
                        bull1 = "yes";
                    }
                    currPol1 = polarities.get(emotion);
                    groups1.put(group);
                    count++;
                    if (count == 1) {
                        rank += emotion;//+ ":" + mood.getValue();
                    } else {
                    }
                } else {
                    if (currPol2 != 0 && polarities.get(emotion) != 0 && (currPol2 != polarities.get(emotion))) {
                        amb2 = "yes";
                    }
                    if (hasBull && (emotion.equals("anger") || emotion.equals("disgust"))) {
                        bull2 = "yes";
                    }
                    currPol2 = polarities.get(emotion);
                    groups2.put(group);
                    countAmb++;
                    if (countAmb == 2) {
                        break;
                    }
                }
            }

            
        }
        if (rank.length() > 0) {
            output.put("ambiguous", amb1);
            output.put("groups", groups1);
            output.put("bullying", bull1);
           
        } else {
            output.put("ambiguous", amb2);
            output.put("groups", groups2);
            output.put("bullying", bull2);
            
        }

        return output.toString();

        
    }

     
    private void loadEmotionGroups() {
        if (groups.size() == 0) {
            for (String lang : this.groupsPaths.keySet()) {
                String groupsPath = groupsPaths.get(lang);
                System.out.println("Loading the Emotion Groups " + groupsPath);
                TreeMap<String, String> group = new TreeMap();

                BufferedReader br = null;

                File folder = new File(groupsPath);

                for (final File fileEntry : folder.listFiles()) {
                    //If it is not a directory, hence is a file
                    if (!fileEntry.isDirectory()) {
                        try {
                            br = new BufferedReader(new InputStreamReader(new FileInputStream(fileEntry.getAbsolutePath()), "UTF8"));
                            //set vote for this group to 0 (init) and details to blank
                            votesBlank.put(fileEntry.getName(), 0);
                            votesDetailBlank.put(fileEntry.getName(), "");

                            String line;
                            while ((line = br.readLine()) != null) {
                                line = line.trim().toLowerCase();

                                if (!group.containsKey(line)) {
                                    group.put(line, fileEntry.getName());
                                } else {
                                    String list = group.get(line);
                                    list += "\t" + fileEntry.getName();
                                    group.put(line, list);

                                }
                            }

                        } catch (FileNotFoundException ex) {
                            Logger.getLogger(Classifier.class
                                    .getName()).log(Level.SEVERE, null, ex);
                        } catch (UnsupportedEncodingException ex) {
                            Logger.getLogger(Classifier.class
                                    .getName()).log(Level.SEVERE, null, ex);
                        } catch (IOException ex) {
                            Logger.getLogger(Classifier.class
                                    .getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                }
                groups.put(lang, group);

                
            }
        }
    }

   
    private void loadEmotionMatrices() {
        if (matrices.size() == 0) {
            for (String lang : this.matricesPaths.keySet()) {
                String emotionsPath = matricesPaths.get(lang);
                System.out.println("Loading the Emotion Matrix " + emotionsPath);
                BufferedReader br = null;
                TreeMap<String, Double> patts = new TreeMap();
                TreeMap<Integer, String> emos = new TreeMap();
                try {
                    File file = new File(emotionsPath);
                    InputStream inputStream = new FileInputStream(file);
                    br = new BufferedReader(new InputStreamReader(inputStream, "UTF8"));
                    // br = new BufferedReader(new InputStreamReader(new FileInputStream(emotionsPath), "UTF8"));
                    String line;
                    while ((line = br.readLine()) != null && !line.contains("Emotions")) {
                        //skip
                    }

                    while ((line = br.readLine()) != null && !line.contains("Patterns")) {
                        if (line.length() > 0) {
                            String tokens[] = line.split("\t");
                            emos.put(Integer.parseInt(tokens[0]), tokens[1]);
                        }
                    }

                    while ((line = br.readLine()) != null && !line.contains("Matrix")) {
                        if (line.length() > 0) {
                            //String tokens[] = line.split("\t");
                            patts.put(line.trim(), 0.0);
                        }
                    }
                    int n = patts.size();
                    int m = emos.size();
                    DoubleMatrix matrix = new DoubleMatrix(m, n);
                    int j = 0;
                    while ((line = br.readLine()) != null) {
                        if (line.length() > 0) {
                            //String tokens[] = line.split("\t");

                            double[] mat = new double[n];
                            String tokens[] = line.split(" ");
                            int i = 0;
                            for (String val : tokens) {
                                mat[i] = Float.parseFloat(val);
                                i++;
                            }
                            DoubleMatrix row = new DoubleMatrix(1, n, mat);
                            matrix.putRow(j, row);
                            j++;
                            row = null;
                        }
                    }
                    Geometry geo = new Geometry();
                    
                    br.close();
                    matrices.put(lang, matrix);
                    patterns.put(lang, patts);
                    emotions.put(lang, emos);
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(Classifier.class.getName()).log(Level.SEVERE, null, ex);
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(Classifier.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(Classifier.class.getName()).log(Level.SEVERE, null, ex);
                } /*finally {
                 try {
                 br.close();
                 } catch (IOException ex) {
                 Logger.getLogger(MatrixPFICFRankClassifier.class.getName()).log(Level.SEVERE, null, ex);
                 } /*finally {
                 try {
                 br.close();
                 } catch (IOException ex) {
                 Logger.getLogger(MatrixPFICFRankClassifier.class.getName()).log(Level.SEVERE, null, ex);
                 }
                 }*/

            }
        }
    }

  
    <K, V extends Comparable<? super V>> SortedSet<Map.Entry<K, V>> entriesSortedByValues(Map<K, V> map) {
        SortedSet<Map.Entry<K, V>> sortedEntries = new TreeSet<>(
                new Comparator<Map.Entry<K, V>>() {
                    @Override
                    public int compare(Map.Entry<K, V> e1, Map.Entry<K, V> e2) {
                        int res = e1.getValue().compareTo(e2.getValue());
                        return res != 0 ? res : 1; // Special fix to preserve items with equal values
                    }
                });
        sortedEntries.addAll(map.entrySet());
        return sortedEntries;
    }

    <K, V extends Comparable<? super V>> SortedSet<Map.Entry<K, V>> entriesSortedByValuesDesc(Map<K, V> map) {
        SortedSet<Map.Entry<K, V>> sortedEntries = new TreeSet<>(
                new Comparator<Map.Entry<K, V>>() {
                    @Override
                    public int compare(Map.Entry<K, V> e2, Map.Entry<K, V> e1) {
                        int res = e1.getValue().compareTo(e2.getValue());
                        return res != 0 ? res : 1; // Special fix to preserve items with equal values
                    }
                });
        sortedEntries.addAll(map.entrySet());
        return sortedEntries;
    }

}
