/*
 * Copyright 2012 Midokura KK
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midolman.mgmt.rest_api.resources;

import com.midokura.midolman.mgmt.auth.AuthAction;
import com.midokura.midolman.mgmt.auth.Authorizer;
import com.midokura.midolman.mgmt.data.dao.ChainDao;
import com.midokura.midolman.mgmt.jaxrs.ForbiddenHttpException;
import com.midokura.midolman.state.NoStatePathException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.util.UUID;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TestChainResource {

    private ChainResource testObject;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private SecurityContext context;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ResourceFactory factory;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private Authorizer auth;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private UriInfo uriInfo;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ChainDao dao;

    @Before
    public void setUp() throws Exception {
        testObject = new ChainResource(uriInfo, context, auth, dao, factory);
    }

    @Test(expected = ForbiddenHttpException.class)
    public void testDeleteUnauthorized() throws Exception {
        // Set up
        UUID id = UUID.randomUUID();
        doReturn(false).when(auth).chainAuthorized(context, AuthAction.WRITE,
                id);

        // Execute
        testObject.delete(id);
    }

    @Test
    public void testDeleteNonExistentData() throws Exception {
        // Set up
        UUID id = UUID.randomUUID();
        doReturn(true).when(auth)
                .chainAuthorized(context, AuthAction.WRITE, id);
        doThrow(NoStatePathException.class).when(dao).delete(id);

        // Execute
        testObject.delete(id);

        // Verify
        verify(dao, times(1)).delete(id);
    }

    @Test(expected = ForbiddenHttpException.class)
    public void testGetUnauthorized() throws Exception {
        // Set up
        UUID id = UUID.randomUUID();
        doReturn(false).when(auth)
                .chainAuthorized(context, AuthAction.READ, id);

        // Execute
        testObject.get(id);
    }
}
