/*
 * Copyright 2012 Midokura KK
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midolman.mgmt.data.dto;

import java.net.URI;
import java.util.UUID;

import javax.validation.GroupSequence;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.groups.Default;
import javax.xml.bind.annotation.XmlRootElement;

import com.midokura.midolman.mgmt.data.dto.Chain.ChainExtended;
import com.midokura.midolman.mgmt.data.dto.config.ChainNameMgmtConfig;
import com.midokura.midolman.mgmt.jaxrs.validation.annotation.IsUniqueChainName;
import com.midokura.midolman.mgmt.jaxrs.ResourceUriBuilder;
import com.midokura.midolman.state.zkManagers.ChainZkManager.ChainConfig;

/**
 * Class representing chain.
 */
@IsUniqueChainName(groups = ChainExtended.class)
@XmlRootElement
public class Chain extends UriResource {

    public static final int MIN_CHAIN_NAME_LEN = 1;
    public static final int MAX_CHAIN_NAME_LEN = 255;

    private UUID id = null;
    private String tenantId = null;

    @NotNull
    @Size(min = MIN_CHAIN_NAME_LEN, max = MAX_CHAIN_NAME_LEN)
    private String name = null;

    /**
     * Default constructor
     */
    public Chain() {
        super();
    }

    /**
     * Constructor
     *
     * @param id
     *            ID of the chain
     * @param config
     *            ChainConfig object
     */
    public Chain(UUID id, ChainConfig config) {
        this(id, config.properties.get(ConfigProperty.TENANT_ID), config.name);
    }

    /**
     * Constructor
     *
     * @param id
     *            ID of the chain
     * @param tenantId
     *            Tenant ID
     * @param name
     *            Chain name
     */
    public Chain(UUID id, String tenantId, String name) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
    }

    /**
     * @return the id
     */
    public UUID getId() {
        return id;
    }

    /**
     * @param id
     *            the id to set
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * @return the tenantId
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * @param tenantId
     *            the tenantId to set
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the self URI
     */
    @Override
    public URI getUri() {
        if (getBaseUri() != null && id != null) {
            return ResourceUriBuilder.getChain(getBaseUri(), id);
        } else {
            return null;
        }
    }

    /**
     * @return the rules URI
     */
    public URI getRules() {
        if (getBaseUri() != null && id != null) {
            return ResourceUriBuilder.getChainRules(getBaseUri(), id);
        } else {
            return null;
        }
    }

    public ChainConfig toConfig() {
        ChainConfig config = new ChainConfig(name);
        if (tenantId != null) {
            config.properties.put(ConfigProperty.TENANT_ID, tenantId);
        }
        return config;
    }

    public ChainNameMgmtConfig toNameMgmtConfig() {
        return new ChainNameMgmtConfig(this.getId());
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "id=" + id + " tenantId=" + tenantId + ", name=" + name;
    }

    /**
     * Interface used for a Validation group. This group gets triggered after
     * the default validations.
     */
    public interface ChainExtended {
    }

    /**
     * Interface that defines the ordering of validation groups.
     */
    @GroupSequence({ Default.class, ChainExtended.class })
    public interface ChainGroupSequence {
    }
}
