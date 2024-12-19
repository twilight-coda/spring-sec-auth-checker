package models;

public class StoreModel {
    private String url;
    private String requestMethod;
    private String preAuthorization;
    private String postAuthorization;
    private String preFilter;
    private String postFilter;

    StoreModel(String url, String requestMethod,
               String preAuthorization,
               String postAuthorization,
               String preFilter,
               String postFilter) {
        this.url = url;
        this.requestMethod = requestMethod;
        this.preAuthorization = preAuthorization;
        this.postAuthorization = postAuthorization;
        this.preFilter = preFilter;
        this.postFilter = postFilter;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getPreAuthorization() {
        return preAuthorization;
    }

    public void setPreAuthorization(String preAuthorization) {
        this.preAuthorization = preAuthorization;
    }

    public String getPostAuthorization() {
        return postAuthorization;
    }

    public void setPostAuthorization(String postAuthorization) {
        this.postAuthorization = postAuthorization;
    }

    public String getPreFilter() {
        return preFilter;
    }

    public void setPreFilter(String preFilter) {
        this.preFilter = preFilter;
    }

    public String getPostFilter() {
        return postFilter;
    }

    public void setPostFilter(String postFilter) {
        this.postFilter = postFilter;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "StoreModel{" +
                "url='" + url + '\'' +
                ", requestMethod='" + requestMethod + '\'' +
                ", preAuthorization='" + preAuthorization + '\'' +
                ", postAuthorization='" + postAuthorization + '\'' +
                ", preFilter='" + preFilter + '\'' +
                ", postFilter='" + postFilter + '\'' +
                '}';
    }
}
