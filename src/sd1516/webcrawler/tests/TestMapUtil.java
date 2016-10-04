package sd1516.webcrawler.tests;

import java.util.HashMap;

import sd1516.webcrawler.utils.MapUtil;
import sd1516.webcrawler.utils.Publication;

public class TestMapUtil {

	public static void main(String[] args){
		
		HashMap<Publication,Integer> hmap = new HashMap<Publication,Integer>();
		
		hmap.put(new Publication("aaa","aaa"), 1);
		hmap.put(new Publication("bbb","bbb"), 6);
		hmap.put(new Publication("ccc","ccc"), 3);
		hmap.put(new Publication("ddd","ddd"), 5);
		hmap.put(new Publication("eee","eee"), 2);
		
		hmap = (HashMap<Publication, Integer>) MapUtil.sortByValue(hmap);
		
		System.out.println(hmap.toString());
	}
}
