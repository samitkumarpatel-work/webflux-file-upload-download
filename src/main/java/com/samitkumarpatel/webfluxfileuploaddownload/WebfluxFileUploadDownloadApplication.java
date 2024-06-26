package com.samitkumarpatel.webfluxfileuploaddownload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

@SpringBootApplication
public class WebfluxFileUploadDownloadApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebfluxFileUploadDownloadApplication.class, args);
	}

	@Bean
	CorsWebFilter corsFilter() {
		CorsConfiguration config = new CorsConfiguration();
		config.addAllowedOriginPattern("*");
		config.addAllowedHeader("*");
		config.addAllowedMethod("*");

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);

		return new CorsWebFilter(source);
	}

	@Bean
	RouterFunction<ServerResponse> routerFunction(FileHandler fileHandler) {
		return RouterFunctions
				.route()
				.path("/person", builder -> builder
						.POST("", contentType(MULTIPART_FORM_DATA), fileHandler::upload)
						.GET("", fileHandler::fetchAll))
				.POST("/upload", contentType(MULTIPART_FORM_DATA), fileHandler::upload)
				.build();
	}

}

@Component
@RequiredArgsConstructor
@Slf4j
class FileHandler {
	final FileService fileService;
	public Mono<ServerResponse> upload(ServerRequest request) {
		return request
				.multipartData()
				.flatMap(stringPartMultiValueMap -> {
					var nameFormFiledPart = (FormFieldPart) stringPartMultiValueMap.get("name").getFirst();
					var name = nameFormFiledPart.value();

					var ageFormFiledPart = (FormFieldPart) stringPartMultiValueMap.get("age").getFirst();
					var age = ageFormFiledPart.value();
					var files = Objects.requireNonNullElse(stringPartMultiValueMap.get("files"), List.of()).stream().map(part -> (FilePart) part).toList();
					log.info("{} , {} has uploaded {} ", name, age, files.stream().map(FilePart::filename).toList());

					return Flux.fromIterable(files)
							.flatMap(f -> {
								return DataBufferUtils
										.join(f.content())
										.map(dataBuffer -> new Documents(null, f.filename(), null, dataBuffer.asByteBuffer().array()));
							})
							.collectList()
							.map(Set::copyOf)
							.map(documents -> new Person(null, name, Integer.parseInt(age), documents));
				})
				.flatMap(fileService::save)
				.flatMap(p -> ServerResponse.ok().bodyValue(p));
	}

	public Mono<ServerResponse> fetchAll(ServerRequest request) {
		return fileService
				.fetchAll()
				.collectList()
				.flatMap(ServerResponse.ok()::bodyValue);
	}
}

interface PersonRepository extends ListCrudRepository<Person, Integer> {}

@Service
@RequiredArgsConstructor
class FileService {
	final PersonRepository personRepository;

	public Mono<Person> save(Person person) {
		return Mono.fromCallable(() -> personRepository.save(person));
	}

	public Flux<Person> fetchAll() {
		return Flux.fromIterable(personRepository.findAll());
	}
}

record Person(@Id Integer id, String name, int age, Set<Documents> documents) {}
record Documents(@Id Integer id, String name, Integer person, byte[] data) {}