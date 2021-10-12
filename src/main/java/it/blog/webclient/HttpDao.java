package it.blog.webclient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.UnknownHttpStatusCodeException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class HttpDao {

	@Autowired
	ObjectMapper objectMapper;
	
	final static String urlMessage = "http://localhost:8080/message";
	final static String urlFrom = "http://localhost:8080/from";
	
	
	public Greeting getGreetingNoReactiveMessage() throws JsonMappingException, JsonProcessingException {
		
		final RestTemplate restTemplate = new RestTemplate();
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		

		HttpEntity<String> entity = new HttpEntity<String>(headers);
		
		try {
			// http://localhost:8080/message
			ResponseEntity<Greeting> message = restTemplate.exchange(urlMessage, HttpMethod.GET, entity, Greeting.class);

			log.info("{} - {}", message.getStatusCode(), message.getBody());
			
			// http://localhost:8080/from
			ResponseEntity<Greeting> from = restTemplate.exchange(urlFrom, HttpMethod.GET, entity, Greeting.class);

			log.info("{} - {}", from.getStatusCode(), from.getBody());						

			return this.mergeMessageWithFrom(message.getBody(), from.getBody()); 
			
		}
		/*
		 * HttpClientErrorException – in case of HTTP status 4xx
		 * HttpServerErrorException – in case of HTTP status 5xx
		 * UnknownHttpStatusCodeException – in case of an unknown HTTP status
		 */
		catch (HttpClientErrorException | HttpServerErrorException | UnknownHttpStatusCodeException errorException) {

			ObjectMapper objectMapper = new ObjectMapper();

			log.error(errorException.getResponseBodyAsString());

			Greeting response = objectMapper.readValue(errorException.getResponseBodyAsString(),
					Greeting.class);	
			
			return response;
		}
	}
	
	public Greeting getGreetingReactiveBlockMessage() throws JsonMappingException, JsonProcessingException {
		try {
			log.info("Starting Message resource");
			Greeting greetingMono = WebClient.create().get().uri(urlMessage).retrieve()
					.bodyToMono(Greeting.class).log().block();

			log.info("End Message resource");
			log.info("Starting From resource");
			
			Greeting greetingFrom = WebClient.create().get().uri(urlFrom).retrieve()
					.bodyToMono(Greeting.class).log().block();
			log.info("End From resource");
			
			greetingMono.setFrom(greetingFrom.getFrom());

			return greetingMono;
		} catch (WebClientResponseException we) {
			log.info("Exception {} - {}", we.getRawStatusCode(), we.getMessage());
			return objectMapper.readValue(we.getResponseBodyAsString(), Greeting.class);
		} catch (WebClientRequestException we) {
			log.info("Exception {}", we.getMessage());
			return new Greeting(we.getMessage());
		}
	}

	public Mono<Greeting> getGreetingReactiveMessage() {

		log.info("Starting Reactive Method!");
		Mono<Greeting> greetingMono = WebClient.create().get().uri(urlMessage).retrieve()
				.bodyToMono(Greeting.class).log()
				/*
				 * doOnNext is just callback that says what to do when Mono above completed
				 * successfully, usually we log something or update some value, but it is not
				 * used to return something or throw exception
				 */
				.doOnNext(greeting -> this.addedWord(greeting))
				.flatMap(greeting -> this.getGreetingFromReactive(greeting))
				.onErrorResume(WebClientResponseException.class, wcre -> this.getNotFoundGreeting(wcre))
				.onErrorResume(WebClientRequestException.class, wcre -> this.getNotFoundGreeting(wcre));

		log.info("Exiting Reactive Method!");
		return greetingMono;

	}

	public Mono<Greeting> getGreetingMessageReactiveExchange() {

		return WebClient.create().get().uri(urlMessage).accept(MediaType.APPLICATION_JSON)
				.exchangeToMono(response -> {
					if (response.statusCode().equals(HttpStatus.OK)) {
						return response.bodyToMono(Greeting.class).flatMap(greeting -> this.getGreetingFromReactive(greeting));
					} else if (response.statusCode().is4xxClientError()) {
						// Suppress error status code
						return response.bodyToMono(Greeting.class);
					} else {
						// Turn to error
						return response.createException().flatMap(Mono::error);
					}
				}).log()
				.onErrorResume(WebClientResponseException.class, wcre -> this.getNotFoundGreeting(wcre))
				.onErrorResume(WebClientRequestException.class, wcre -> this.getNotFoundGreeting(wcre));

	}

	public Mono<Greeting> getGreetingMessageReactiveParallel() {

		Mono<Greeting> msg = getGreetingMsgReactive().subscribeOn(Schedulers.boundedElastic());
		Mono<Greeting> from = getGreetingFromReactive().subscribeOn(Schedulers.boundedElastic());

		return Mono.zip(msg, from, Greeting::new);

	}

	private Mono<Greeting> addedWord(Greeting greeting) {
		greeting.setMessage(greeting.getMessage() + " - Reactive!");
		return Mono.fromCallable(() -> greeting);
	}

	private Mono<Greeting> getGreetingFromReactive() {

		log.info("getGreetingFromReactive!");
		Mono<Greeting> greetingMono = WebClient.create().get().uri(urlFrom).retrieve()
				.bodyToMono(Greeting.class).log()
				.onErrorResume(WebClientResponseException.class, wcre -> this.getNotFoundGreeting(wcre));

		log.info("Exiting getGreetingFromReactive!");
		return greetingMono;

	}

	private Mono<Greeting> getGreetingFromReactive(Greeting greeting) {

		log.info("getGreetingFromReactive!");
		Mono<Greeting> greetingMono = WebClient.create().get().uri(urlFrom).retrieve()
				.bodyToMono(Greeting.class).log().map(from -> this.mergeMessageWithFrom(greeting, from))
				.onErrorResume(WebClientResponseException.class, wcre -> this.getNotFoundGreeting(wcre));

		log.info("Exiting getGreetingFromReactive!");
		return greetingMono;

	}

	private Mono<Greeting> getGreetingMsgReactive() {

		log.info("Starting getGreetingMsgReactive!");
		Mono<Greeting> greetingMono = WebClient.create().get().uri(urlMessage).retrieve()
				.bodyToMono(Greeting.class).log()
				/*
				 * doOnNext is just callback that says what to do when Mono above completed
				 * successfully, usually we log something or update some value, but it is not
				 * used to return something or throw exception
				 */
				.doOnNext(greeting -> this.addedWord(greeting))
				.onErrorResume(WebClientResponseException.class, wcre -> this.getNotFoundGreeting(wcre))
				.onErrorResume(WebClientRequestException.class, wcre -> this.getNotFoundGreeting(wcre));

		log.info("Exiting getGreetingMsgReactive!");
		return greetingMono;

	}

	private Mono<Greeting> getNotFoundGreeting(WebClientResponseException wcre) {
		log.info("Exception {} - {}", wcre.getRawStatusCode(), wcre.getMessage());
		return Mono.fromCallable(() -> objectMapper.readValue(wcre.getResponseBodyAsString(), Greeting.class));

	}

	private Mono<Greeting> getNotFoundGreeting(WebClientRequestException wcre) {
		log.info("Exception {}", wcre.getMessage());
		return Mono.fromCallable(() -> new Greeting(wcre.getMessage()));
	}

	private Greeting mergeMessageWithFrom(Greeting greeting, Greeting from) {
		greeting.setFrom(from.getFrom());
		return greeting;
	}
}