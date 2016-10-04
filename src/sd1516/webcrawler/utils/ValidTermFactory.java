package sd1516.webcrawler.utils;

import alice.tuprolog.Term;

/**
 * questa classe serve per costruire delle LogicTuple valide: 
 * la tupla è valida se ha un ' all'inizio e uno alla fine ma nessuno nel mezzo. 
 * Con il primo metodo, tutti gli eventuali ' nel mezzo vengono sostituiti con la stringa !CH39! 
 * Con il secondo metodo !CH39! viene ri-trasformato in '
*/

public class ValidTermFactory {

	private ValidTermFactory(){};
	
	/**
	 * metodo usato dal worker per convertire una stringa in Term da mandare sul centro delle tuple
	 * */
	
	public static Term getTermByString(String s){
		Term t = null;
		
		if(s.contains("!CH39!")){ //se la stringa(titolo o url di una pub) contiene !CH39! ci diamo alle scienze umane
			throw new IllegalArgumentException();
		}
		
		if((s.charAt(0) == (char)39) && s.charAt(s.length()-1) == (char)39){ //se sia il primo sia l'ultimo carattere sono ' 
			s = s.substring(1, s.length()-1); //prendo la sottostringa che non li comprende
		}
		
		s = s.replaceAll("'", "!CH39!"); //sostituisco a ogni eventuale ' in mezzo, la sequenza !CH39!
		s = s.replaceAll("\n", " ");
		t = Term.createTerm("'"+s+"'"); //creo un term(che il worker manderà sul centro delle tuple) con la stringa e i giusti '
		
		return t;
	}
	
	/**
	 * metodo usato dal master per convertire un Term in stringa
	 * */
	
	public static String getStringByTerm(Term t){
		String s = t.toString(); //trasformo il term in stringa
		if((s.charAt(0) == '[') && s.charAt(s.length()-1) == ']'){ //se il primo e l'ultimo carattere sono [ o ]
			s = s.substring(1, s.length()-1); //prendo la sottostringa che non li comprende
		}
		s = s.substring(1, s.length()-1); //rimuovo anche gli apici
		
		if(s.contains("!CH39!")){ //se la stringa contiene la sequenza !CH39! (che era stata messa dal worker con il metodo sopra)
			s = s.replaceAll("!CH39!", ""+(char)39); //la sostituisco con '
		}
		
		return s;
	}
}