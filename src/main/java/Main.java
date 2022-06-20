import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.lang.reflect.Array;
import java.net.URL;
import java.util.*;
import java.io.*;

public class Main {
    final static String PDF_PATH = "vocab.pdf";
    final static String DICTIONARY_PATH = "dict";
    final static String[] blacklist = { "make", "unpleasant", "mysterious", "baneful" };

    public static void main(String[] args) {
        String raw = getTextFromPDF();

        // delimit
        Scanner in = new Scanner(raw);
        ArrayList<Set<String>> words;
        Set<String> current = new TreeSet<>();
        words = new ArrayList<Set<String>>();

        while (in.hasNextLine()) {
            String line = in.nextLine();
            //System.out.println(line);
            if (line.isEmpty())
                continue;

            if ((line.startsWith("LESSON")) && !current.isEmpty()) {
                words.add(new TreeSet<>(current));
                //System.out.println(current);
                current.clear();
            } else if (Character.isDigit(line.charAt(0))) {
                ArrayList<String> tokens = new ArrayList<>(Arrays.asList(line.split(" ")));
                // every other token
                for (String token: tokens)
                    if (!Character.isDigit(token.charAt(0))) {
                        // first index of a number

                        StringBuffer buffer = new StringBuffer(token);
                        for (int i = 0; i < buffer.length(); i++)
                            if (!Character.isLetter(buffer.charAt(i)))
                                buffer.replace(i, i+1, "");
                        if (buffer.length() >= 3 && !Arrays.asList(blacklist).contains(buffer.toString())) {
                            // minimum size for most words is about 3 (ish)
                            current.add(buffer.toString());
                        }
                    }
            }
        }
        words.add(current); // add last

        ArrayList<Map<String, ArrayList<String>>> definitions = new ArrayList<>();
        Map<String, String> nullWords = new TreeMap<>();
        int wordCount = 0;

        try {
            // get dictionary definitions
            String path = DICTIONARY_PATH;
            URL url = new URL("file", null, path);
            IDictionary dict = new Dictionary(url);
            dict.open();

            int setIndex = 0;
            for (Set<String> list: words) {
                definitions.add(new TreeMap<>());
                for (String word: list) { // 600
                    ++wordCount;

                    for (POS p: POS.values()) { // 1000<
                        IIndexWord idxWord = dict.getIndexWord(word, p);
                        if (idxWord != null) { // 692
                            for (IWordID wordID: idxWord.getWordIDs()) {
                                IWord found = dict.getWord(wordID);
                                // removes any regionalized/context definitions - not necessary
                                if (found.getSynset().getGloss().charAt(0) == '(')
                                    continue;
                                String gloss = found.getSynset().getGloss();
                                if (gloss.contains(";"))
                                    gloss = gloss.substring(0, gloss.indexOf(";"));
                                // removing unnecessary parts
                                String definition = "(" + p +") " + gloss;
                                if (definitions.get(setIndex).containsKey(word))
                                    definitions.get(setIndex).get(word).add(definition);
                                else {
                                    ArrayList<String> word_defs = new ArrayList<>();
                                    word_defs.add(definition);
                                    definitions.get(setIndex).put(word, word_defs);
                                }
                                break;
                            }
                        }
                    }

                    if (!definitions.get(setIndex).containsKey(word)) {
                        nullWords.put(word, "Set " + (setIndex+1));
                        definitions.get(setIndex).put(word, new ArrayList<>(Arrays.asList("NO DEFINITION AVAILABLE")));
                    }
                }
                setIndex++;
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        // Print
        int definitionCount = 0;
        for (int set = 0; set < definitions.size(); set++) {
            System.out.println("VOCAB SET " + (set+1) + ": \n");
            int index = 0;
            for (String word : definitions.get(set).keySet()) {
                ArrayList<String> defs = definitions.get(set).get(word);
                System.out.print(word + ": " + defs.get(0));
                for (int i = 1; i < defs.size(); i++)
                    System.out.print(", " + defs.get(i));
                System.out.println();
                definitionCount += defs.size();
            }
            System.out.println("\n\n");
        }

        System.out.println("Statistics:\n");
        System.out.println("Word Count: " + wordCount);
        System.out.println("Definitions: " + definitionCount);
        System.out.println("Words without definitions: " + nullWords.size() + "\n");
        int nullWordPos = 0;
        for (String nullWord: nullWords.keySet())
            System.out.println(++nullWordPos + ": " + nullWord + " -> " + nullWords.get(nullWord));
    }

    public static String getTextFromPDF() {
        try {
            File pdfFile = new File(PDF_PATH);
            PDDocument PDF = PDDocument.load(pdfFile);
            PDFTextStripper PDFStripper = new PDFTextStripper();
            String raw = PDFStripper.getText(PDF);
            PDF.close();
            return raw;
        } catch (IOException e) {
            System.out.println("ERR: Could not read file \"vocab.pdf\" due to " + e.getCause());
            e.printStackTrace();
            System.exit(-1);
            return null;
        }
    }
}
