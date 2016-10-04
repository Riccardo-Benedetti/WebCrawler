package sd1516.webcrawler.tests;

import sd1516.webcrawler.utils.PreParser;

public class TestPreParser {

	public static void main(String[] args) {
		int a = 4; //2 //3 //uso lo switch case così si deve cambaire solo il numerino della variabile a
		String pre ="";
		switch (a) {
		case 1:
			pre = "@article{compfields-scp117, Author = {Damiani, Ferruccio and Viroli, Mirko and Beal, "
					+ "Jacob}, Doi = {10.1016/j.scico.2015.11.005}, Issn = {0167-6423}, Journal = {Science "
					+ "of Computer Programming}, Keywords = {Computational field, Core calculus, "
					+ "Operational semantics, Spatial computing, Type soundness}, Pages = {17--44}, "
					+ "Title = {A type-sound calculus of computational fields}, "
					+ "Url = {http://www.sciencedirect.com/science/article/pii/S0167642315003573}, "
					+ "Volume = 117, Year = 2016}";
			break;
		case 2:
			pre = "@inproceedings{GossipingCoordination2016, author = {Pianini, Danilo and Beal, Jacob "
					+ "and Viroli, Mirko}, title = {Improving Gossip Dynamics Through Overlapping "
					+ "Replicates}, booktitle = {Coordination Models and Languages - 18th {IFIP} {WG}"
					+ " 6.1 International Conference, {COORDINATION} 2016, Held as Part of the 11th"
					+ " International Federated Conference on Distributed Computing Techniques, DisCoTec"
					+ " 2016, Heraklion, Crete, Greece, June 6-9, 2016, Proceedings}, pages = {192--207}, "
					+ "year = {2016}, url = {http://dx.doi.org/10.1007/978-3-319-39519-7_12}, doi = "
					+ "{10.1007/978-3-319-39519-7_12}, editor = {Alberto Lluch Lafuente and Jos{\'{e}}"
					+ " Proen{\\c{c}}a}, series = {Lecture Notes in Computer Science}, volume = {9686}, "
					+ "publisher = {Springer}, year = {2016}, url = {http://dx.doi.org/10.1007/978-3-319-39519-7},"
					+ " doi = {10.1007/978-3-319-39519-7}, isbn = {978-3-319-39518-0} }";
			break;
		case 3:
			pre = "@incollection{Bratman90, Address = {Cambridge, MA}, Author = {Bratman, Michael E.}, "
					+ "Booktitle = {Intentions in Communication}, Editor = {Cohen, Philip R. and Morgan, Jerry L. "
					+ "and Pollack, Martha E.}, Isbn = {978-0-262-03150-9}, Isbn-10 = {0-262-03150-7}, Month = "
					+ "jun, Pages = {15--32}, Publisher = {The MIT Press}, Title = {What is Intention?}, Url ="
					+ " {http://mitpress.mit.edu/catalog/item/default.asp?tid=5832&ttype=2}, Year = 1990}";
			break;
		case 4:
			pre = "@article{fuzzydl-91,"+
				  "author    = {Yen, John},"+
				  "title     = {Generalizing Term Subsumption Languages to Fuzzy Logic},"+
				  "booktitle = {IJCAI},"+
				  "year      = 1991,"+
				  "pages     = {472--477},"+
				  "bibsource = {DBLP, http://dblp.uni-trier.de}"+
				"}";
		}
		
		System.out.println("KEYWORDS: "+PreParser.getTagInfo(pre, "keyword"));
		System.out.println("AUTHORS: "+PreParser.getTagInfo(pre, "author"));
		System.out.println("TITLE: "+PreParser.getTagInfo(pre, "title"));
	}
}
