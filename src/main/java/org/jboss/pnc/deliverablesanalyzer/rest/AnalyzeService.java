/*
 * Copyright (C) 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.deliverablesanalyzer.rest;

import javax.annotation.security.PermitAll;
import javax.enterprise.context.ApplicationScoped;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.hibernate.validator.constraints.URL;
import org.jboss.pnc.build.finder.core.BuildConfig;
import org.jboss.pnc.deliverablesanalyzer.model.FinderResult;
import org.jboss.pnc.deliverablesanalyzer.model.FinderStatus;
import org.jboss.resteasy.annotations.jaxrs.FormParam;
import org.jboss.resteasy.annotations.jaxrs.PathParam;

@ApplicationScoped
@Path("analyze")
public interface AnalyzeService {
    @GET
    @Path("configs/{id}")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    BuildConfig configs(@NotEmpty @Pattern(regexp = "^[a-f0-9]{8}$") @PathParam String id);

    @GET
    @Path("results/{id}")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    FinderResult results(@NotEmpty @Pattern(regexp = "^[a-f0-9]{8}$") @PathParam String id);

    @GET
    @Path("statuses/{id}")
    @PermitAll
    @Produces(MediaType.APPLICATION_JSON)
    FinderStatus statuses(@NotEmpty @Pattern(regexp = "^[a-f0-9]{8}$") @PathParam String id);

    @POST
    @PermitAll
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    Response analyze(@NotEmpty @FormParam @URL(regexp = "^http(s)?:.*") String url, @FormParam String config);
}
