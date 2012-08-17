/*
 * Copyright 2011 Midokura KK
 * Copyright 2012 Midokura PTE LTD.
 */

package com.midokura.midolman.mgmt.data.zookeeper.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.inject.Inject;
import com.midokura.midolman.mgmt.data.dao.AdRouteDao;
import com.midokura.midolman.mgmt.data.dto.AdRoute;
import com.midokura.midolman.state.zkManagers.AdRouteZkManager;
import com.midokura.midolman.state.NoStatePathException;
import com.midokura.midolman.state.StateAccessException;

import javax.naming.OperationNotSupportedException;

/**
 * Data access class for advertising route.
 */
public class AdRouteDaoImpl implements AdRouteDao {

    private final AdRouteZkManager dataAccessor;

    /**
     * Constructor
     *
     * @param dataAccessor
     *            　　AdRoute data accessor.
     */
    @Inject
    public AdRouteDaoImpl(AdRouteZkManager dataAccessor) {
        this.dataAccessor = dataAccessor;
    }

    @Override
    public UUID create(AdRoute adRoute) throws StateAccessException {
        return dataAccessor.create(adRoute.toConfig());
    }

    @Override
    public AdRoute get(UUID id) throws StateAccessException {
        try {
            return new AdRoute(id, dataAccessor.get(id));
        } catch (NoStatePathException e) {
            return null;
        }
    }

    @Override
    public List<AdRoute> findByBgp(UUID bgpId) throws StateAccessException {
        List<AdRoute> adRoutes = new ArrayList<AdRoute>();
        List<UUID> ids = dataAccessor.list(bgpId);
        for (UUID id : ids) {
            adRoutes.add(new AdRoute(id, dataAccessor.get(id)));
        }
        return adRoutes;
    }

    @Override
    public void delete(UUID id) throws StateAccessException {
        dataAccessor.delete(id);
    }

    @Override
    public void update(AdRoute adRoute) throws StateAccessException {
        throw new UnsupportedOperationException();
    }
}
