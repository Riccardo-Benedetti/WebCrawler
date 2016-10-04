package sd1516.webcrawler.utils;

/**
 * Classe che contiene il metodo per recuperare keywords, autori, titoli, ecc... delle pubblicazioni
 */

public class PreParser {

	private PreParser(){}
	
	public static String getTagInfo(String pre, String tag){ //"pre" è il blocco @...{..., ..., ...} 
		String words = "";
		
		String preToLowerCase = pre.toLowerCase(); //mette minuscola tutta la stringa perchè indexOf() è case sensitive
		int tagIndex = preToLowerCase.indexOf((char)9+tag); //prendo indice dove inizia il tag
		if(tagIndex<0){
			tagIndex = preToLowerCase.indexOf((char)32+tag);
			if(tagIndex<0){
				tagIndex = preToLowerCase.indexOf(tag);
			}
		}
		
		if(tagIndex>1){ //se c'è un tag e quindi un indice di inizio
			words = pre.substring(tagIndex); //rimuovo la parte prima del tag
			words = words.substring(words.indexOf("{")+1); //rimuovo la parte: Tag = {
			int fromBrace = 0; //è la posizione dove inzia la prima parola della lista tra le graffe
			int toBrace = words.indexOf("}"); // è la posizione della prima } che si incontra
			
			/**
			 * rimuovo eventuali { } che si potrebbero trovare all'interno delle graffe di Tag = {...} 
			 */
			while(words.indexOf("{") < words.indexOf("}") && words.indexOf("{") >= 0){ //se trovo una { all'interno di Tag = {...} 
				int openCB = words.indexOf("{"); //posizione della { intrusa
				int closedCB = words.indexOf("}"); //posizione della prima } intrusa
				StringBuilder sb = new StringBuilder(words);
				sb.deleteCharAt(openCB); //rimuovo la { intrusa
				sb.deleteCharAt(closedCB-1); //rimuovo la } intrusa, il cui indice ci è spostato indietro di 1 per il delete della {
				words = sb.toString();
				toBrace = words.indexOf("}"); //posizione della prossima } che è l'ultima
				
				/**
				 * rimuovo eventuali caratteri inutili che si potrebbero trovare all'interno delle graffe di Keywords = {...} 
				 */
				if((words.charAt(openCB) == '/') && (words.charAt(openCB+1) == 39)){ //se trovo / o '
					StringBuilder sb2 = new StringBuilder(words);
					sb2.deleteCharAt(openCB); //li rimuovo
					sb2.deleteCharAt(openCB);
					words = sb2.toString();
					toBrace = words.indexOf("}"); //posizione della prossima } che è l'ultima
				}
			}
			
			words = words.substring(fromBrace, toBrace); //prendo le parole tra la prima posizione salvata precedentemente e la posizione dell'ultima } che chiude Tag = {...}  
		}
		
		words = words.replace("/sf", ""); //elimino eventuali caratteri "/sf" che compaiono nelle pubblicazioni
		
		return words; //torna stringa contenente eventuali tag
	}
}
