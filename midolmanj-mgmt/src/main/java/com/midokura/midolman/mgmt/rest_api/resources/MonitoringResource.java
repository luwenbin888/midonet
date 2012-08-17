/*
 * Copyright (c) 2012 Midokura Pte.Ltd.
 */

package com.midokura.midolman.mgmt.rest_api.resources;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.servlet.RequestScoped;
import com.midokura.midolman.mgmt.auth.AuthRole;
import com.midokura.midolman.mgmt.data.dao.MetricDao;
import com.midokura.midolman.mgmt.data.dto.Metric;
import com.midokura.midolman.mgmt.data.dto.MetricQuery;
import com.midokura.midolman.mgmt.data.dto.MetricQueryResponse;
import com.midokura.midolman.mgmt.data.dto.MetricTarget;
import com.midokura.midolman.mgmt.http.VendorMediaType;
import com.midokura.midolman.state.StateAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Date: 5/3/12
 */
@RequestScoped
public class MonitoringResource {

    private final static Logger log = LoggerFactory
        .getLogger(MonitoringResource.class);

    private final MetricDao dao;

    @Inject
    public MonitoringResource(MetricDao dao) {
        this.dao = dao;
    }

    /**
     * This method will execute the query against the collected metrics data
     * store and return the results.
     *
     * @param query      the query that the system will process
     * @return MetricQueryResponse with the results of the query
     * @throws StateAccessException
     */
    @POST
    @Path("/query")
    @RolesAllowed({AuthRole.ADMIN})
    @Consumes({VendorMediaType.APPLICATION_MONITORING_QUERY_JSON,
                  MediaType.APPLICATION_JSON})
    @Produces({VendorMediaType.APPLICATION_MONITORING_QUERY_RESPONSE_JSON,
                  MediaType.APPLICATION_JSON})
    public MetricQueryResponse post(MetricQuery query)
        throws StateAccessException {

        return dao.executeQuery(query);
    }

    /**
     * This method gets all the metrics associated with an object from the data
     * store.
     *
     * @param target     we want to get all the metric for this object
     * @return List of Metric for this target
     * @throws StateAccessException
     */
    @POST
    @Path("/filter")
    @RolesAllowed({AuthRole.ADMIN})
    @Consumes(
        {VendorMediaType.APPLICATION_METRIC_TARGET_JSON,
                MediaType.APPLICATION_JSON})
    @Produces({VendorMediaType.APPLICATION_METRICS_COLLECTION_JSON,
                  MediaType.APPLICATION_JSON})
    public List<Metric> getMetricResource(MetricTarget target)
        throws StateAccessException {

        return dao.listMetrics(target.getType(), target.getTargetIdentifier());
    }
}
