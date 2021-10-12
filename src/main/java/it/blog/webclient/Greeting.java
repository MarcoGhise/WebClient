package it.blog.webclient;

public class Greeting {

	private String message;
	private String from;

	public Greeting() {
	}

	public Greeting(String message) {
		this.message = message;
	}
	
	public Greeting(Greeting greetingMsg, Greeting greetingFrom) {
		this.setMessage(greetingMsg.getMessage());
		this.setFrom(greetingFrom.getFrom());
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	@Override
	public String toString() {
		return "Greeting [message=" + message + ", from=" + from + "]";
	}
}
