// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.filter;

import com.google.inject.Inject;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.vespa.hosted.controller.restapi.filter.config.HttpAccessControlConfig;
import com.yahoo.yolean.chain.After;
import com.yahoo.yolean.chain.Before;
import com.yahoo.yolean.chain.Provides;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.jdisc.http.HttpRequest.Method.OPTIONS;
import static com.yahoo.vespa.hosted.controller.restapi.filter.AccessControlHeaders.ACCESS_CONTROL_HEADERS;
import static com.yahoo.vespa.hosted.controller.restapi.filter.AccessControlHeaders.ALLOW_ORIGIN_HEADER;

/**
 * <p>
 * This filter makes sure we respond as quickly as possible to CORS pre-flight requests
 * which browsers transmit before the Hosted Vespa dashboard code is allowed to send a "real" request.
 * </p>
 * <p>
 * An "Access-Control-Max-Age" header is added so that the browser will cache the result of this pre-flight request,
 * further improving the responsiveness of the Hosted Vespa dashboard application.
 * </p>
 * <p>
 * Runs after all standard security request filters, but before BouncerFilter, as the browser does not send
 * credentials with pre-flight requests.
 * </p>
 *
 * @author andreer
 * @author gv
 */
@After({"InputValidationFilter","RemoteIPFilter", "DoNotTrackRequestFilter", "CookieDataRequestFilter"})
@Before({"BouncerFilter", "ControllerAuthorizationFilter"})
@Provides("AccessControlRequestFilter")
public class AccessControlRequestFilter implements SecurityRequestFilter {
    private final Set<String> allowedUrls;

    @Inject
    public AccessControlRequestFilter(HttpAccessControlConfig config) {
        allowedUrls = Collections.unmodifiableSet(config.allowedUrls().stream().collect(Collectors.toSet()));
    }

    @Override
    public void filter(DiscFilterRequest discFilterRequest, ResponseHandler responseHandler) {
        String origin = discFilterRequest.getHeader("Origin");

        if (!discFilterRequest.getMethod().equals(OPTIONS.name()))
            return;

        HttpResponse response = HttpResponse.newInstance(Response.Status.OK);

        if (allowedUrls.contains(origin))
            response.headers().add(ALLOW_ORIGIN_HEADER, origin);

        ACCESS_CONTROL_HEADERS.forEach(
                (name, value) -> response.headers().add(name, value));

        ContentChannel cc = responseHandler.handleResponse(response);
        cc.close(null);
    }
}
