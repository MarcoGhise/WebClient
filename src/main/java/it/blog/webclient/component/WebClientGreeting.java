package it.blog.webclient.component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClient.UriSpec;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import it.blog.webclient.Greeting;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class WebClientGreeting {

	private static Logger log = LoggerFactory.getLogger(WebClientGreeting.class);

	public static WebClient getWebClientForJson(String url) {
		return WebClient.builder().baseUrl(url).defaultCookie("cookieKey", "cookieValue")
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultUriVariables(Collections.singletonMap("url", url)).build();
	}

	public static WebClient getWebClientWithTimeout() {
		HttpClient httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 50000)
				.responseTimeout(Duration.ofMillis(50000))
				.doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(50000, TimeUnit.MILLISECONDS))
						.addHandlerLast(new WriteTimeoutHandler(50000, TimeUnit.MILLISECONDS)));

		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();

	}

	public static WebClient getWebClientWithHandlerException() {
		ExchangeFilterFunction handlerResponseFilter = ExchangeFilterFunction
				.ofResponseProcessor(WebClientGreeting::exchangeFilterResponseProcessor);

		ExchangeFilterFunction handlerRequestFilter = ExchangeFilterFunction
				.ofRequestProcessor(WebClientGreeting::exchangeFilterRequestProcessor);

		// return WebClient.builder().filter(errorResponseFilter).build();
		return WebClient //
				.builder() //
				.filters(exchangeFilterFunctions -> {
					exchangeFilterFunctions.add(handlerResponseFilter);
					exchangeFilterFunctions.add(handlerRequestFilter);
				}) //
				.build();

	}

	public static RequestHeadersSpec<?> prepareRequest(WebClient client, HttpMethod method, String path) {
		/*
		 * Define the Method
		 */
		UriSpec<RequestBodySpec> uriSpec = client.method(method);
		/*
		 * Define the URL
		 */
		RequestBodySpec bodySpec = uriSpec.uri(uriBuilder -> uriBuilder.pathSegment(path).build());
		/*
		 * Define the Body
		 */
		RequestHeadersSpec<?> headersSpec = bodySpec.body(BodyInserters.fromValue("data"));

		return headersSpec;

	}

	public static Mono<ResponseEntity<Greeting>> callingEntity(RequestHeadersSpec<?> headersSpec) {
		/*
		 * Making a call with extra headers
		 */
		ResponseSpec responseSpec = headersSpec //
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE) //
				.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML) //
				.acceptCharset(StandardCharsets.UTF_8) //
				.ifNoneMatch("*") //
				.ifModifiedSince(ZonedDateTime.now()).retrieve();

		Mono<ResponseEntity<Greeting>> greetingMono = responseSpec.toEntity(Greeting.class);

		return greetingMono;
	}

	private static Mono<ClientResponse> exchangeFilterResponseProcessor(ClientResponse clientResponse) {

		log.info("exchangeFilterResponseProcessor {}", clientResponse);

		HttpStatus status = clientResponse.statusCode();
		if (status.is5xxServerError()) {
			return clientResponse.bodyToMono(String.class).flatMap(body -> Mono.just(clientResponse));
		}
		if (status.is4xxClientError()) {
			// return clientResponse.bodyToMono(String.class).flatMap(body ->
			// Mono.just(clientResponse));
			return Mono.error(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Entity not found."));
		}

		log.info(String.valueOf(clientResponse.statusCode()));

		// return Mono.just(response);
		return Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST).body("Ops...something goes wrong").build());
		// return Mono.just(clientResponse);
	}

	private static Mono<ClientRequest> exchangeFilterRequestProcessor(ClientRequest clientRequest) {

		if (log.isDebugEnabled()) {
			StringBuilder sb = new StringBuilder("Request: \n");
			// append clientRequest method and url
			clientRequest.headers().forEach((name, values) -> values.forEach(value -> {
				sb.append(name);
				sb.append(values);
			}));
			log.debug(sb.toString());
		}
		return Mono.just(clientRequest);
	}
	
	public static Mono<Greeting> handleAllPossibleExceptionPost(WebClient client, Greeting greeting) {

		log.info("Calling handleAllPossibleExceptionPost with Greeting {}", greeting);

		Mono<Greeting> result = client.post() //
				.uri("http://localhost:8081/greeting/{from}", greeting.getFrom())//
				.contentType(MediaType.APPLICATION_JSON) //
				.bodyValue(greeting) //
				.retrieve() //
				/*
				 * Catch Http error status
				 */
				.onStatus(HttpStatus::isError, response -> {
					
					ErrorType type = null;
					
					if (response.statusCode().is4xxClientError()) {
						log.info("Response from service is 4xx");
						type = ErrorType.HTTPSTATUS4XX;
					}
					else {
						log.info("Response from service is 5xx");
						type = ErrorType.HTTPSTATUS5XX;
					}

					return Mono.error(new WebClientException("Service response non 200", type));
				}) //
				.bodyToMono(Greeting.class) //
				/*
				 * Catch all no http error status detected (Connection refused, timeout
				 * connection, host not found, ...
				 */
				.onErrorMap(Predicate.not(WebClientException.class::isInstance), throwable -> {
					log.error("Failed to send requesto to service", throwable);
					return new WebClientException("Failed to send requesto to service", ErrorType.NETWORK);
				})
				/*
				 * Catch all error above
				 */
				.doOnError(error -> log.error("caught error", error));

		return result;

	}

	// https://medium.com/a-developers-odyssey/spring-web-client-exception-handling-cd93cf05b76
	// https://www.baeldung.com/spring-webflux-timeout#exception-handling
	
	public static Mono<Greeting> handleAllPossibleException(WebClient client, Greeting greeting) {
		Mono<Greeting> result = client.get() //
				.uri("http://localhost:8081/message") //
				.retrieve() //
				/*
				 * Catch Http error status
				 */
				.onStatus(HttpStatus::isError, response -> {
					
					ErrorType type = null;
					
					if (response.statusCode().is4xxClientError()) {
						log.info("Response from service is 4xx");
						type = ErrorType.HTTPSTATUS4XX;
					}
					else {
						log.info("Response from service is 5xx");
						type = ErrorType.HTTPSTATUS5XX;
					}

					return Mono.error(new WebClientException("Service response non 200", type));
				}) //
				.bodyToMono(Greeting.class) //
				/*
				 * Catch all no http error status detected (Connection refused, timeout
				 * connection, host not found, ...
				 */
				.onErrorMap(Predicate.not(WebClientException.class::isInstance), throwable -> {
					log.error("Failed to send requesto to service", throwable);
					return new WebClientException("Failed to send requesto to service", ErrorType.NETWORK);
				})
				/*
				 * Catch all error above
				 */
				.doOnError(error -> log.error("caught error", error));

		return result;
	}

}
