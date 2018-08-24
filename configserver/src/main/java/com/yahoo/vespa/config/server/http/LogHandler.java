package com.yahoo.vespa.config.server.http;

import com.google.inject.Inject;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.Admin;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.Set;

public class LogHandler extends HttpHandler {

    private final SuperModelProvider superModelProvider;

    @Inject
    public LogHandler(HttpHandler.Context ctx, SuperModelProvider superModelProvider){
        super(ctx);
        this.superModelProvider = superModelProvider;
    }

    @Override
    public HttpResponse handleGET(HttpRequest request) {
        String logServerURL = getLogServerURL();
        TenantName tenantName = Utils.getTenantNameFromApplicationsRequest(request);
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(logServerURL);
        try {
            org.apache.http.HttpResponse response = client.execute(get);
            return new HttpResponse(response.getStatusLine().getStatusCode()) {
                @Override
                public void render(OutputStream outputStream) throws IOException {
                    outputStream.write(response.getEntity().getContent().read());
                }
            };
        } catch (IOException e) {
            return null;
        }

    }
    private String getLogServerURL() {
        Set<ApplicationId> applicationIds = superModelProvider.getSuperModel().getAllApplicationIds();
        VespaModel model = null;
        for (ApplicationId applicationId : applicationIds) {
            Optional<ApplicationInfo> info = superModelProvider.getSuperModel().getApplicationInfo(applicationId);
            if(info.isPresent() && info.get().getModel() instanceof VespaModel) {
                model = (VespaModel) info.get().getModel();
                break;
            }
        }
        if (model == null) {
            return null;
        }
        Admin admin = model.getAdmin();
        return admin.getLogserver().getHostName();

    }
}
