package com.samitkumarpatel.webfluxfileuploaddownload;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.http.codec.multipart.FilePartEvent;
import org.springframework.http.codec.multipart.FormPartEvent;
import org.springframework.http.codec.multipart.PartEvent;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

@SpringBootApplication
public class WebfluxFileUploadDownloadApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebfluxFileUploadDownloadApplication.class, args);
	}

	@Bean
	RouterFunction<ServerResponse> routerFunction(FileHandler fileHandler) {
		return RouterFunctions
				.route()
				.POST("/upload", contentType(MULTIPART_FORM_DATA), fileHandler::upload)
				.build();
	}

}

@Component
@RequiredArgsConstructor
class FileHandler {
	final FileService fileService;
	public Mono<ServerResponse> upload(ServerRequest request) {

		Map<String, String> formData = new HashMap<>();
		Map<String, Flux<DataBuffer>> fileData = new HashMap<>();

		return request
				.bodyToFlux(PartEvent.class)
				.windowUntil(PartEvent::isLast)
				.concatMap(p -> p.switchOnFirst((signal, partEvents) -> {
					if (signal.hasValue()) {
						PartEvent event = signal.get();
						if (event instanceof FormPartEvent formEvent) {
							System.out.println("FormPartEvent: " + formEvent.name() + " " + formEvent.value());
							formData.put(formEvent.name(), formEvent.value());
						}
						else if (event instanceof FilePartEvent fileEvent) {
							String filename = fileEvent.filename();
							Flux<DataBuffer> contents = partEvents.map(PartEvent::content);
							System.out.println("FilePartEvent: " + filename);
							fileData.put(filename, contents);
						}
						else {
							return Mono.error(new RuntimeException("Unexpected event: " + event));
						}
						return Mono.empty();
					}
					else {
						return Mono.error(new RuntimeException("Unexpected signal: " + signal));
					}
				}))
				.then(Objects.requireNonNull(personMapper(formData, fileData)))
				.flatMap(fileService::save)
				.flatMap(person -> ServerResponse.ok().bodyValue(person));
	}

	private Mono<Person> personMapper(Map<String, String> formData, Map<String, Flux<DataBuffer>> fileData) {

		//return Mono.fromCallable(() -> new Person(null, formData.get("name"), Integer.parseInt(formData.get("age")), null));

		return Flux.fromIterable(fileData.entrySet())
				.flatMap(entry -> {
					String filename = entry.getKey();
					Flux<DataBuffer> content = entry.getValue();
					//return DataBufferUtils.join(content).map(dataBuffer -> new Documents(null, filename, null, dataBuffer.asByteBuffer().array()));
					return Mono.fromCallable(() -> new Documents(null, filename, null, "".getBytes(StandardCharsets.UTF_8)));
				})
				.collectList()
				.map(Set::copyOf)
				.map(documents -> new Person(null, formData.get("name"), Integer.parseInt(formData.get("age")), documents));
	}
}

interface PersonRepository extends ListCrudRepository<Person, Integer> {}

@Service
@RequiredArgsConstructor
class FileService {
	final PersonRepository personRepository;
	public Mono<?> save(Person person) {
		return Mono.fromCallable(() -> personRepository.save(person));
	}
}

record Person(@Id Integer id, String name, int age, Set<Documents> documents) {}
record Documents(@Id Integer id, String name, Integer person, byte[] data) {}