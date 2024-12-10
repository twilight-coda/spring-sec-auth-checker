# Spring Security Specification Checker

The analysis will be done in two passes.

The first pass collects the information of all the endpoints -
1. The list of handler methods.
2. Information about handler method annotations.
3. Information about annotations on Contoller classes (that contain handler methods).

The second pass analyzes the security filter chain. It uses the information from pass 1, combines it with the security chain configuration and produces the security configuration for each endpoint.

## Pass 1 - AST traversal and Annotation Extraction

For this phase, we will traverse the AST to locate handler methods, the endpoints whose request they handle, and any security configurations specified using Spring Security annotations.

The output of this stage will be an object described as follows:
```JSON
{
    "<url>": {
        "request_method": "String",
        "pre_authorization": "String",
        "post_authorization": "String",
        "pre_filter": "String",
        "post_filter": "String"
    }
}
```

### Endpoint to Handler Mapping

There are a few ways using which URLs are mapped to handlers. These are discussed below.

#### Controller Classes

Spring Controllers are annotated with `@Controller` or `@RestController`. These annotations declare these classes as Spring components responsible for handling requests.

These classes can optionally be annotated with `@RequestMapping` in which case, the values passed to the annotation will be prefixed to the endpoints defined for the individual handler methods.

```Java
@RestController
@RequestMapping("/persons")
class PersonController {

    // This method handles requests to "/persons/{id}" endpoint.
	@GetMapping("/{id}")
	public Person getPerson(@PathVariable Long id) { }

}
```

#### Handler Methods

`@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, and `@PatchMapping` can be used to annotate a method. The annotation element value declares the URL fragement the annotated method will handle (e.g., a method annotated with `@GetMapping('/version')` will handle a request with the endpoint `'/version'`).

```Java
@RestController
class PersonController {

    // This method handles requests to "/persons/{id}" endpoint.
	@GetMapping("/persons/{id}")
	public Person getPerson(@PathVariable Long id) { }

}
```

#### Implemented Interfaces and Superclasses

If a superclass/interface contains annotated methods, a class that extends/implements it will use the same annotation element values and as a result will handle requests for endpoints declared in the superclass/interface.

```Java
public interface MyApi {
    @GetMapping("/items")
    List<Item> getItems();
}

@RestController
public class MyController implements MyApi {
    // Inherits the @GetMapping("/items") annotation
    public List<Item> getItems() { }
}
```

#### Custom Annotations

Custom meta-annotations can be created with request mapping annotations. Consequently, using these custom annotations effectively also annotates a class or method with request mapping annotations.


### Spring Security Annotations

Spring supports method level authorization modeling. These provide a way to specify authorization logic at method-level.
These guards are checked **after** the request-level authorization performed by the security filter chain.
They can be used in conjunction with, or as an alternative to request-level authorization. Also, these annotations can be applied to classes which makes all the methods inherit the configuration. 

The security specifications passed to the these annotations are written in Spring Expression Language (SpEL).

Following are the annotations provided by Spring Security:

#### `@PreAuthorize`

This annotation provides authorization logic that will be executed before a method is executed (but after passing the security filter chain's security check).

```Java
@Service
public class MyCustomerService {
    @PreAuthorize("hasAuthority('permission:read')")
    public Customer readCustomer(String id) { ... }
}
```

SpEL support boolean operators so, multiple checks can specified within a single annotation. Repeating an annotation on the same method is not supported.

#### `@PostAuthorize`

The authorization logic specified through this annotation is checked after the method executes - this could be used for guarding a method based on the output.
```Java
@Service
public class MyCustomerService {
    @PostAuthorize("returnObject.owner == authentication.name")
    public Customer readCustomer(String id) { ... }
}
```
The example above checks if a property in the returned object matches the name property of the authentication object.

#### `@PreFilter`

This is applied to a method that takes a collection as an argument. It takes a filter predicate that is applied to the input collection. Only the items from the collection that pass this predicate check are passed to the method.

```Java
@Component
public class BankService {
	@PreFilter("filterObject.owner == authentication.name")
	public Collection<Account> updateAccounts(Account... accounts) {
        // ... `accounts` will only contain the accounts owned by the logged-in user
        return updated;
	}
}
```

#### `@PostFilter`

Same as `@PreFilter` but the predicate is applied to the returned value.

```Java
@Component
public class BankService {
	@PostFilter("filterObject.owner == authentication.name")
	public Collection<Account> readAccounts(String... ids) {
        // ... the return value will be filtered to only contain the accounts owned by the logged-in user
        return accounts;
	}
}
```

#### Meta-annotations

It is also possible to create meta-annotations that can use any combination of the annotations mentioned above. Following is an example:

```Java
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface IsAdmin {}

...

@Component
public class BankService {
	@IsAdmin
	public Account readAccount(Long id) {
        // ... is only returned if the `Account` belongs to the logged in user
	}
}
```

#### Additional Considerations

1. Because authorization annotations support SpEL, there should be a parser to generate an AST for the expressions.
2. It is possible to pass any arbitrary string instead of a valid predicate. The analysis should raise an error if this case is encountered.
3. A Pre or Post filter requires that the annotated method takes a collection as an argument. But this is only checked at run-time and throws an error. The analysis should raise an error if the annotated method does not follow this rule.

#### Analysis Steps

1. Iterate through all custom annotations to create a map from each custom annotation to request mapping annotations. This will allow us to lookup a URL fragments or security configurations that may exist as meta-annotations.
2. Iterate over each Contoller class.
    1. If the class is annotated with a request mapping use the value as a URL fragment for later steps.
    2. For each method in the controller class
        1. If the method does not have a request mapping annotation, then skip to the next method.
        2. Otherwise,
            1. Get the URL fragment, concatenate it to the Controller class's fragment, store it as the URL.
            2. Store the request method according to the request mapping annotation used.
            3. If there is a `@PreAuthorize` annotation, add the predicate to the store. If the predicate is not valid, register it as an error.
            4. If there is a `@PostAuthorize` annotation, add the predicate to the store. If the predicate is not valid, register it as an error.
            5. If there is a `@PreFilter` annotation, add the predicate to the store.If the predicate is not valid, register it as an error.
            6. If there is a `@PostFilter` annotation, add the predicate to the store. If the predicate is not valid, register it as an error.
 



## Pass 2 - Dataflow analysis

- A dataflow analysis will be performed on the security filter chain.
- The construction of this filter chain follows the builder pattern and multiple configurations can be provided in a single chain.
- The store will be a set of all endpoint configurations analyzed.
- Each configuration starts with a request matcher (using the method `requestMatchers`)
- Wherever it can be statically determined, the analysis of `requestMatchers` will yield the endpoint(s) and the REST method.
- A request matcher is followed by the authorization check. A request may:
	- be public
	- denied
	- only allow authenticated users
	- only allow users with certain authorities
	- determine access based on custom logic
- This part of the security chain will be analyzed to get the authentication/authorization part of the store.

### Transfer functions for `requestMatchers`

The list below describes the transfer function for each matcher. $f$ is the transfer function and $S$ is the  store.

$S_0$ = $\phi$: The initial store.

$M$: The Map derived from AST analysis in pass 1.

For each matcher/authorization pair $i$, the transfer function transforms the store using the following rule:

$S_i = f(i) = S_{i-1} \cup \{ (e_i, m_i, a_i, preA_j, postA_j, preF_j, postF_j) \mid i \in \text{Matchers}, j \in M \}$

Where each tuple now includes:

- $e_i$: endpoint or predicate if defined using custom logic (details in the custom matchers section).
- $m_i$: HTTP method
  - if not specified - ANY
  - otherwise - GET, PUT, POST, etc.
- $a_i$: security attributes, derived from various security annotations:
  - `permitAll` : "ALL"
  - `denyAll` : "NONE"
  - `authenticated` : "AUTHENTICATED"
  - `hasAuthority(authority)` or `hasAnyAuthority(authorities...)` : $\{a \mid a \in \text{authorities}\}$
  - `hasRole(role)` or `hasAnyRole(roles...)` : $\{r \mid r \in \text{roles}\}$
  - `access(predicate)` : predicate
- $preA_j$:
  - `preAuthorize(predicate)` - predicate
- $postA_j$:
  - `postAuthorize(predicate)` - predicate
- $preF_j$:
  - `preFilter(predicate)` - predicate
- $postF_j$:
  - `postFilter(predicate)` - predicate

Input and output value for $preA_j, postA_j, preF_j, postF_j$ values remain the same in this dataflow analysis, so they are not mentioned in the examples below. They are however included in the store.


The next sections describe how the transfer function updates the store in each case.

#### 1. Simple matcher

```Java
@Bean
public SecurityFilterChain web(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests((authorize) -> authorize
	    .requestMatchers("/endpoint").hasAuthority("USER")
            .anyRequest().authenticated()
        )
        // ...

    return http.build();
}
```

##### Transfer Function
`.requestMatchers("/endpoint").hasAuthority("USER")`:
$f(S) = S \cup \{\text{("/endpoint", ANY, \{"USER"\})}\}$

`.anyRequest().authenticated()`
$f(S) = S \cup \{\text{("/endpoint2", ANY, "AUTHENTICATED")}\} \cup \{\text{("/endpoint3", ANY, "AUTHENTICATED")}\}$

#### 2. AntMatchers

```Java
// wildcard matching
http
    .authorizeHttpRequests((authorize) -> authorize
        .requestMatchers("/resource/**").hasAuthority("USER")
        .anyRequest().authenticated()
    );
```

##### Transfer Function
`.requestMatchers("/resource/**").hasAuthority("USER")`:
$f(S) = S \cup \{\text{("/resource/one", ANY, \{"USER"\})}\} \cup \{\text{("/resource/two", ANY, \{"USER"\})}\}$

`.anyRequest().authenticated()`
$f(S) = S \cup \{\text{("/endpoint2", ANY, "AUTHENTICATED")}\} \cup \{\text{("/endpoint3", ANY, "AUTHENTICATED")}\}$

#### 3. Using Regular Expressions

```Java
http
    .authorizeHttpRequests((authorize) -> authorize
        .requestMatchers(RegexRequestMatcher.regexMatcher("/resource/[A-Za-z0-9]+")).hasAuthority("USER")
        .anyRequest().denyAll()
    );
```

##### Transfer Function
`.requestMatchers(RegexRequestMatcher.regexMatcher("/resource/[A-Za-z0-9]+")).hasAuthority("USER")`
$f(S) = S \cup \{\text{("/resource/a", ANY, \{"USER"\})}\} \cup \{\text{("/resource/123bcd", ANY, \{"USER"\})}\}$

`.anyRequest().authenticated()`
$f(S) = S \cup \{\text{("/endpoint2", ANY, "AUTHENTICATED")}\} \cup \{\text{("/endpoint3", ANY, "AUTHENTICATED")}\}$
#### 4. Match using HTTP method only

```Java
http
    .authorizeHttpRequests((authorize) -> authorize
        .requestMatchers(HttpMethod.GET).hasAuthority("read")
        .requestMatchers(HttpMethod.POST).hasAuthority("write")
        .anyRequest().denyAll()
    );
```

##### Transfer Function

`.requestMatchers(HttpMethod.GET).hasAuthority("read")`:
$f(S) = S \cup \{\text{("/resource1", GET, \{"read"\})}\} \cup \{\text{("/resource2", GET, \{"read"\})}\} \cup \{\text{("/resource/sub1", GET, \{"read"\})}\}$

`.requestMatchers(HttpMethod.POST).hasAuthority("write")`:
$f(S) = S \cup \{\text{("/resource3", POST, \{"write"\})}\} \cup \{\text{("/resource4", POST, \{"write"\})}\} \cup \{\text{("/resource/sub2", POST, \{"write"\})}\}$

`.anyRequest().denyAll()`:
$f(S) = S \cup \{\text{("/resource/a", ANY, "NONE")}\} \cup \{\text{("/resource/123bcd", ANY, "NONE")}\}$

#### 5. MVC Matcher

```Java
@Bean
MvcRequestMatcher.Builder mvc(HandlerMappingIntrospector introspector) {
	return new MvcRequestMatcher.Builder(introspector).servletPath("/spring-mvc");
}

@Bean
SecurityFilterChain appEndpoints(HttpSecurity http, MvcRequestMatcher.Builder mvc) {
	http
        .authorizeHttpRequests((authorize) -> authorize
            .requestMatchers(mvc.pattern("/my/controller/**")).hasAuthority("controller")
            .anyRequest().authenticated()
        );

	return http.build();
}
```

##### Transfer Function

`.requestMatchers(mvc.pattern("/my/controller/**")).hasAuthority("controller")`:
$f(S) = S \cup \{\text{("/spring-mvc/my/controller/resource1", ANY, \{"controller"\})}\} \cup \{\text{("/spring-mvc/my/controller/resource2", ANY, \{"controller"\})}\}$

`.anyRequest().authenticated()`
$f(S) = S \cup \{\text{("/endpoint2", ANY, "AUTHENTICATED")}\} \cup \{\text{("/endpoint3", ANY, "AUTHENTICATED")}\}$


### Transfer functions for custom authorization and matchers

There are ways to provide custom configuration for authorization and request matchers. We will encode this logic into SMT theories so they can be verified later.
#### 1. Authorization Manager

```Java
http
    .authorizeHttpRequests((authorize) -> authorize
        .requestMatchers("/resource/{name}").access(new WebExpressionAuthorizationManager("#name == authentication.name"))
        .anyRequest().authenticated()
    )
```

##### Transfer Function

`.requestMatchers("/resource/{name}").access(new WebExpressionAuthorizationManager("#name == authentication.name"))`:
$f(S) = S \cup \{\text{("/resource/\{name\}", ANY, "name == authenticationName"}\}$

`.anyRequest().authenticated()`:
$f(S) = S \cup \{\text{("/endpoint2", ANY, AUTHENTICATED)}\} \cup \{\text{("/endpoint3", ANY, AUTHENTICATED)}\}$

#### 2. Custom Matcher

```Java
RequestMatcher printview = (request) -> request.getParameter("print") != null;

http
    .authorizeHttpRequests((authorize) -> authorize
        .requestMatchers(printview).hasAuthority("print")
        .anyRequest().authenticated()
    )
```

##### Transfer Function

`.requestMatchers(printview).hasAuthority("print")`:
$f(S) = S \cup \{\text{("requestParam == "print")", ANY, \{print\}}\}$

`.anyRequest().authenticated()`:
$f(S) = S \cup \{\text{("/endpoint2", ANY, AUTHENTICATED)}\} \cup \{\text{("/endpoint3", ANY, AUTHENTICATED)}\}$
