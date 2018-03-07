/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jammin.text.emotion;

/**
 *
 * @author Carlos
 */
public class RegularExpressions {

    public static final String cwRegex = "((?![@,#])[\\p{L}\\p{M}*\\p{N}_]+|(?![@,#])\\p{Punct}+)";
    public static final String delimRegex = "((?![@,#])\\b|\\p{Z}+|$|^|(?![@,#])\\p{Punct})";
    public static final String token_delimRegex = " \r\n\t.,;:\"()¿?¡!\'";
    public static final String punctRegex = "[\\p{Punct},¿¡]";
    public static final String htRegex = "#([\\p{L}\\p{M}*\\p{N}_]+|(?![@,#])\\p{Punct}+)";
    public static final String umRegex = "@([\\p{L}\\p{M}*\\p{N}_]+|(?![@,#])\\p{Punct}+)";
    public static final String urlRegex = "http://([\\p{L}\\p{M}*\\p{N}_\\.\\/]+|(?![@,#])\\p{Punct}+)";
    
}
