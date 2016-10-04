package sd1516.webcrawler.utils;

public class Publication {

	private String title;
	private String url;
	
	public Publication(String title, String url){
		this.title = title;
		this.url = url;
	}
	
	public String getTitle(){
		return this.title;
	}
	
	public String getUrl(){
		return this.url;
	}
}
