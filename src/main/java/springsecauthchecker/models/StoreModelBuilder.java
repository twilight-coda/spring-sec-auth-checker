package springsecauthchecker.models;

public class StoreModelBuilder {
    private String url;
    private String requestMethod;
    private String preAuthorization;
    private String postAuthorization;
    private String preFilter;
    private String postFilter;

    public StoreModelBuilder setUrl(String url) {
        this.url = url;
        return this;
    }

    public StoreModelBuilder setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
        return this;
    }

    public StoreModelBuilder setPreAuthorization(String preAuthorization) {
        this.preAuthorization = preAuthorization;
        return this;
    }

    public StoreModelBuilder setPostAuthorization(String postAuthorization) {
        this.postAuthorization = postAuthorization;
        return this;
    }

    public StoreModelBuilder setPreFilter(String preFilter) {
        this.preFilter = preFilter;
        return this;
    }

    public StoreModelBuilder setPostFilter(String postFilter) {
        this.postFilter = postFilter;
        return this;
    }

    public StoreModel createStoreModel() {
        return new StoreModel(url, requestMethod, preAuthorization, postAuthorization, preFilter, postFilter);
    }
}