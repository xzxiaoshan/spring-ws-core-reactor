# spring-ws-core-reactor
以spring-ws-core为基础的reactor模式WebService

## 使用方法

本文只列出和 spring 官方 spring-ws-core 基于 servlet 的 webservice 的配置的区别。  
官方默认的入口类为 MessageDispatcherServlet，通过配置 servlet 拦截 url 进行处理。  
本项目因为是基于 reactor 的，所以代码中没有 servlet 的影子，request 和 response 对应的为 ServerHttpRequest 和 ServerHttpResponse 对象。  
使用方法自然也就是基于自己的拦截方式的，比如Filter，引入该库之后，需要自己定义拦截入口，将内容派发给处理器即可。

1、添加 pom 依赖
```xml
<dependency>
    <groupId>io.github.xzxiaoshan</groupId>
    <artifactId>spring-ws-core-reactor</artifactId>
    <version>0.0.1</version>
</dependency>
```

2、创建url拦截类

本例以 spring-gateway 中的 WebFilter 来拦截为例，如下：

```java
/**
 * WebServiceDispatcher 入口过滤器
 *
 * @author 单红宇
 * @date 2022-05-10 13:02
 */
@Component
@Slf4j
public class WebServiceDispatcherFilter implements WebFilter {

    @Autowired
    private ReactorMessageDispatcher reactorMessageDispatcher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        String requestPath = exchange.getRequest().getPath().pathWithinApplication().value();

        if (requestPath.startsWith("/webservice")) {
            try {
                return reactorMessageDispatcher.doService(exchange.getRequest(), exchange.getResponse());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return chain.filter(exchange);
        }
    }
}
```

3、创建配置类

```java
/**
 * WebService 配置类
 *
 * @author 单红宇
 * @date 2022-05-10 13:02
 */
@EnableWs
@Configuration
public class WebServiceConfiguration extends WsConfigurerAdapter {

    @Bean
    public ReactorMessageDispatcher reactorMessageDispatcher() {
        ReactorMessageDispatcher reactorMessageDispatcher = new ReactorMessageDispatcher();
        reactorMessageDispatcher.setTransformWsdlLocations(true);
        return reactorMessageDispatcher;
    }

    @Bean(name = "countries")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema countriesSchema) {
        DefaultWsdl11Definition wsdl11Definition = new DefaultWsdl11Definition();
        wsdl11Definition.setPortTypeName("CountriesPort");
        wsdl11Definition.setLocationUri("/webservice");
        wsdl11Definition.setTargetNamespace("http://spring.io/guides/gs-producing-web-service");
        wsdl11Definition.setSchema(countriesSchema);
        return wsdl11Definition;
    }

    @Bean
    public XsdSchema countriesSchema() {
        return new SimpleXsdSchema(new ClassPathResource("countries.xsd"));
    }

}
```

> 其中发布的 countries 使用的方法详见 spring 官方示例 https://github.com/spring-guides/gs-producing-web-service
