package it.blog.webclient.component;

public class WebClientException extends Exception {
	
	private ErrorType type;
	
	public WebClientException(String message, ErrorType type) {
		super(message);
		this.type = type;		
	}

	public ErrorType getType() {
		return type;
	}

	public void setType(ErrorType type) {
		this.type = type;
	}
	

}
