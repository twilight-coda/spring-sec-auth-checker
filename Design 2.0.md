
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

The list below describes the transfer function for each matcher. $f$ is the transfer function and $S$ is the initial store.
The state is defined as follows:
$S = \{ (e_i, m_i, a_i, ) \mid i \in \text{Matchers} \}$
- $e_i$: endpoint
- $m_i$: method
	- if not specified - ANY
	- otherwise - GET, PUT, POST etc. 
- $a_i$: security attributes
	- `permitAll` - ALL
	- `denyAll` - NONE
	- `authenticated` - AUTHENTICATED
	- `hasAuthority(authority)` or `hasAnyAuthority(authorities...)` - $\{auth_i \mid i \in \text{authorities}\}$
	- `hasRole(role)` or `hasAnyRole(roles...)` - $\{role_i \mid i \in \text{roles}\}$
	- `access(predicate)` - This method takes custom authorization logic, the analysis for this is described later

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
$f(S) = S \cup \{\text{("/endpoint", ANY, \{USER\})}\}$

`.anyRequest().authenticated()`
$f(S) = S \cup \{\text{("/endpoint2", ANY, AUTHENTICATED)}\} \cup \{\text{("/endpoint3", ANY, AUTHENTICATED)}\}$

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
$f(S) = S \cup \{\text{("/resource/one", ANY, \{USER\})}\} \cup \{\text{("/resource/two", ANY, \{USER\})}\}$

`.anyRequest().authenticated()`
$f(S) = S \cup \{\text{("/endpoint2", ANY, AUTHENTICATED)}\} \cup \{\text{("/endpoint3", ANY, AUTHENTICATED)}\}$

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
$f(S) = S \cup \{\text{("/resource/a", ANY, \{USER\})}\} \cup \{\text{("/resource/123bcd", ANY, \{USER\})}\}$

`.anyRequest().authenticated()`
$f(S) = S \cup \{\text{("/endpoint2", ANY, AUTHENTICATED)}\} \cup \{\text{("/endpoint3", ANY, AUTHENTICATED)}\}$
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
$f(S) = S \cup \{\text{("/resource1", GET, \{read\})}\} \cup \{\text{("/resource2", GET, \{read\})}\} \cup \{\text{("/resource/sub1", GET, \{read\})}\}$

`.requestMatchers(HttpMethod.POST).hasAuthority("write")`:
$f(S) = S \cup \{\text{("/resource3", POST, \{write\})}\} \cup \{\text{("/resource4", POST, \{write\})}\} \cup \{\text{("/resource/sub2", POST, \{write\})}\}$

`.anyRequest().denyAll()`:
$f(S) = S \cup \{\text{("/resource/a", ANY, NONE)}\} \cup \{\text{("/resource/123bcd", ANY, NONE)}\}$

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
$f(S) = S \cup \{\text{("/spring-mvc/my/controller/resource1", ANY, \{controller\})}\} \cup \{\text{("/spring-mvc/my/controller/resource2", ANY, \{controller\})}\}$

`.anyRequest().authenticated()`
$f(S) = S \cup \{\text{("/endpoint2", ANY, AUTHENTICATED)}\} \cup \{\text{("/endpoint3", ANY, AUTHENTICATED)}\}$


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
$f(S) = S \cup \{\text{("/resource/\{name\}", ANY, "(= (param1 authName))"}\}$

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
$f(S) = S \cup \{\text{("(= (requestParam "print"))", ANY, \{print\}}\}$

`.anyRequest().authenticated()`:
$f(S) = S \cup \{\text{("/endpoint2", ANY, AUTHENTICATED)}\} \cup \{\text{("/endpoint3", ANY, AUTHENTICATED)}\}$
