package it.blog.webclient;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.blog.webclient.component.ErrorType;
import it.blog.webclient.component.WebClientException;
import it.blog.webclient.component.WebClientGreeting;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/")
@Slf4j
public class ServerController {

	@Autowired
	HttpDao client;
	
	@Autowired
	ObjectMapper objectMapper;

	@GetMapping(value = "/message", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Greeting> ok200() throws InterruptedException {

		log.info("/message");
		
		Greeting greeting = new Greeting("Hello World");

		Thread.sleep(1000);

		return new ResponseEntity<>(greeting, HttpStatus.OK);
	}

	@PostMapping(value = "/greeting/{from}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Greeting> greetingFrom(@PathVariable("from") String from, @RequestBody Greeting payload) throws InterruptedException {

		log.info("/greeting/{}", from);
		
		Greeting greeting = new Greeting();
		greeting.setFrom(from);
		greeting.setMessage(payload.getMessage());

		return new ResponseEntity<>(greeting, HttpStatus.OK);
	}

	
	@GetMapping(value = "/from", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Greeting> from() throws InterruptedException {

		log.info("/from");
		
		Greeting greeting = new Greeting();
		greeting.setFrom("Milan");

		Thread.sleep(1000);

		return new ResponseEntity<>(greeting, HttpStatus.OK);
	}

	@GetMapping(value = "/ko404", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Greeting> ko404() {

		log.info("/ko404");
		
		Greeting greeting = new Greeting("Hello World");

		return new ResponseEntity<>(greeting, HttpStatus.NOT_FOUND);
	}

	@GetMapping(value = "/ko500", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Greeting> ko500() {
		
		log.info("/ko500");
		
		Greeting greeting = new Greeting("Hello World");

		return new ResponseEntity<>(greeting, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@PostMapping(value = "/ok201", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Greeting> ok201(@RequestParam(value = "name") String name) {
		
		log.info("/ok201");
		
		Greeting greeting = new Greeting("Hello World");

		return new ResponseEntity<>(greeting, HttpStatus.CREATED);
	}

	@GetMapping(value = "/clientReactiveBlock", produces = MediaType.APPLICATION_JSON_VALUE)
	public Greeting client(HttpServletResponse response) throws JsonMappingException, JsonProcessingException {

		log.info("/clientReactiveBlock");
		
		Greeting greeting = client.getGreetingReactiveBlockMessage();

//		response.setStatus(HttpStatus.NOT_FOUND.value());

		return greeting;
	}
	
	@GetMapping(value = "/clientReactive", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<Greeting> clientReactive(HttpServletResponse response)
			throws JsonMappingException, JsonProcessingException {

		log.info("/clientReactive");
		
		Mono<Greeting> greeting = client.getGreetingReactiveMessage();

//		response.setStatus(HttpStatus.NOT_FOUND.value());

		return greeting;
	}

	@GetMapping(value = "/clientNoReactive", produces = MediaType.APPLICATION_JSON_VALUE)
	public Greeting clientNoReactive(HttpServletResponse response) throws JsonMappingException, JsonProcessingException {

		log.info("/clientNoReactive");
		
		Greeting greeting = client.getGreetingNoReactiveMessage();

//		response.setStatus(HttpStatus.NOT_FOUND.value());

		return greeting;
	}

	@GetMapping(value = "/clientExchange", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<Greeting> clientReactiveExchange(HttpServletResponse response)
			throws JsonMappingException, JsonProcessingException {

		log.info("/clientExchange");
		
		Mono<Greeting> greeting = client.getGreetingMessageReactiveExchange();

//		response.setStatus(HttpStatus.NOT_FOUND.value());

		return greeting;
	}

	@GetMapping(value = "/clientReactiveParallel", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<Greeting> clientReactiveParallel(HttpServletResponse response)
			throws JsonMappingException, JsonProcessingException {

		log.info("/clientReactiveParallel");
		
		Mono<Greeting> greeting = client.getGreetingMessageReactiveParallel();

//		response.setStatus(HttpStatus.NOT_FOUND.value());

		return greeting;
	}
	
	@GetMapping(value = "/handleAllPossibleException", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<Greeting> clientHandleAllPossibleException(HttpServletResponse response)
			throws JsonMappingException, JsonProcessingException {

		log.info("/handleAllPossibleException");
		
		Greeting payload = new Greeting();
		payload.setFrom("Milan");
		payload.setMessage("Have a nice day");
		
		Mono<Greeting> greeting = WebClientGreeting.handleAllPossibleException(WebClientGreeting.getWebClientWithTimeout(), payload);


		return greeting;
	}
	
	@PostMapping(value = "/handleAllPossibleException", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<Greeting> clientHandleAllPossibleExceptionPost(HttpServletResponse response)
			throws JsonMappingException, JsonProcessingException {

		log.info("/handleAllPossibleException");
		
		Greeting payload = new Greeting();
		payload.setFrom("Milan");
		payload.setMessage("Have a nice day");
		
		Mono<Greeting> greeting = WebClientGreeting.handleAllPossibleExceptionPost(WebClientGreeting.getWebClientWithTimeout(), payload);


		return greeting;
	}

	/*
	 * Fields missing
	 */
	@ExceptionHandler({ MissingServletRequestParameterException.class })
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public String handleMissingServletRequestParameter(MissingServletRequestParameterException ex, WebRequest request)
			throws JsonProcessingException {

		return ex.getParameterName() + ":" + ex.getMessage();

	}
	
	@ExceptionHandler({ WebClientException.class })
	public ResponseEntity<Greeting> handleWebClientRequestException(WebClientException ex, WebRequest request)
			throws JsonProcessingException {

		if (ex.getType()==ErrorType.HTTPSTATUS4XX)
				return new ResponseEntity<Greeting>(new Greeting(ex.getMessage()), HttpStatus.BAD_REQUEST);
		if (ex.getType()==ErrorType.HTTPSTATUS5XX)
			return new ResponseEntity<Greeting>(new Greeting(ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
		
		return new ResponseEntity<Greeting>(new Greeting(ex.getMessage()), HttpStatus.SERVICE_UNAVAILABLE);

	}

}
