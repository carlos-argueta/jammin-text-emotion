package jammin.text.emotion;

import java.util.Scanner;
/**
 *
 * @author carlos
 */
public class JamminTextEmotion {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Classifier classifier =  new Classifier(15, 7, 3);
        Scanner keyboard = new Scanner(System.in);
        
        String text = "";
        String result = "";

        while(true){
            System.out.print("Type some social text: ");
            text = keyboard.nextLine();
            result = classifier.evalBurts(text.toLowerCase(), "en");
            System.out.println("Result String " + result);
        }
        
    }
    
}
