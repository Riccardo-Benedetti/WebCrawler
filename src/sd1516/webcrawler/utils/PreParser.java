package sd1516.webcrawler.utils;

/**
 * DISTRIBUTED, FAULT-TOLERANT WEB CRAWLING WITH RASPI
 * 
 * @page https://apice.unibo.it/xwiki/bin/view/Courses/Sd1516Projects-CrawlingRaspiRamilliBenedetti
 * 
 * @author Riccardo Benedetti & Elisabetta Ramilli
 * @email riccardo.benedetti3@studio.unibo.it
 * @email elisabetta.ramilli@studio.unibo.it
 * 
 * Alma Mater Studiorum - Università di Bologna
 * Laurea Magistrale in Ingegneria e Scienze Informatiche
 * (Corso di Sistemi Distribuiti - Prof. Andrea Omicini & Stefano Mariani)
 * 
 */

/*
 * Utility that allows to obtain some publication informations
 * parsing the "Pre" tag 
 * (look at TestPreParser.java to see some "pre" examples)
 */
public class PreParser {

	private PreParser(){}
	
	/*
	 * "pre" is the block @...{..., ..., ...} containing the Publication informations.
	 * The "tag" parameter specifies the kind of information to explore (like author, title,
	 * keywords, year, ecc...)
	 */
	public static String getTagInfo(String pre, String tag){
		String words = "";
		
		// indexOf() method is case sensitive
		String preToLowerCase = pre.toLowerCase();
		
		//get the beginning index of tag
		int tagIndex = preToLowerCase.indexOf((char)9+tag); // search for eventual horizontal tab before tag
		if(tagIndex<0){ // not found
			tagIndex = preToLowerCase.indexOf((char)32+tag); // search for eventual whitespace before tag
			if(tagIndex<0){ // not found
				tagIndex = preToLowerCase.indexOf(tag); // search only for tag
			}
		}
		
		if(tagIndex>1){ // tag found
			words = pre.substring(tagIndex); // remove all the "pre" part before the tag index
			words = words.substring(words.indexOf("{")+1); // remove the part: Tag = {
			int fromBrace = 0; // position of the first word into the braces
			int toBrace = words.indexOf("}"); // position of the closing brace
			
			/*
			 * remove possible nested opening/closing braces inside the Tag = {...} 
			 */
			while(words.indexOf("{") < words.indexOf("}") && words.indexOf("{") >= 0){ // in case of opening brace inside Tag = {...} 
				int openCB = words.indexOf("{"); // position of the opening brace intruder
				int closedCB = words.indexOf("}"); // position of the first closing brace intruder
				StringBuilder sb = new StringBuilder(words);
				sb.deleteCharAt(openCB); // remove the opening brace intruder
				sb.deleteCharAt(closedCB-1); // remove the closing brace intruder (shift index caused by the opening brace deletion)
				words = sb.toString();
				toBrace = words.indexOf("}"); // position of the tag closing brace
				
				/*
				 * remove possible useless char inside the tag Keywords = {...} 
				 */
				if((words.charAt(openCB) == '/') && (words.charAt(openCB+1) == 39)){ // looking for slash or apostrophe chars
					StringBuilder sb2 = new StringBuilder(words);
					sb2.deleteCharAt(openCB); // delete it
					sb2.deleteCharAt(openCB);
					words = sb2.toString();
					toBrace = words.indexOf("}"); // position of the tag closing brace
				}
			}
			
			words = words.substring(fromBrace, toBrace); // get all the words inside Tag = {...}  
		}
		
		words = words.replace("/sf", ""); // clean from possible "/sf" (Latex format traces to not consider)
		
		return words;
	}
}
