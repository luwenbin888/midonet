/*
 * Copyright (c) 2014 Midokura Europe SARL, All Rights Reserved.
 */
package org.midonet.api.l4lb;

import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.midonet.api.VendorMediaType;
import org.midonet.api.validation.MessageProperty;
import org.midonet.api.zookeeper.StaticMockDirectory;
import org.midonet.client.dto.*;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static javax.ws.rs.core.Response.Status.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static org.midonet.api.validation.MessageProperty.POOL_MEMBER_WEIGHT_NEGATIVE;
import static org.midonet.api.validation.MessageProperty.RESOURCE_EXISTS;
import static org.midonet.api.validation.MessageProperty.RESOURCE_NOT_FOUND;
import static org.midonet.api.VendorMediaType.APPLICATION_POOL_MEMBER_JSON;
import static org.midonet.api.VendorMediaType.APPLICATION_POOL_MEMBER_COLLECTION_JSON;

@RunWith(Enclosed.class)
public class TestPoolMember {


    public static class TestPoolMemberCrud extends L4LBTestBase {

        @Before
        public void setUp() {
            super.setUp();
        }

        @After
        public void resetDirectory() throws Exception {
            StaticMockDirectory.clearDirectoryInstance();
        }

        private void verifyNumberOfPoolMembers(int num) {
            DtoPoolMember[] poolMembers = getPoolMembers(topLevelPoolMembersUri);
            assertEquals(num, poolMembers.length);
        }

        private void checkBackrefs(URI poolUri, DtoPoolMember... expectedMembers) {
            DtoPool pool = getPool(poolUri);
            DtoPoolMember[] actualMembers = getPoolMembers(pool.getPoolMembers());
            assertEquals(expectedMembers.length, actualMembers.length);

            List<DtoPoolMember> actualList = Arrays.asList(actualMembers);
            for (DtoPoolMember expectedMember : expectedMembers)
                assertTrue(actualList.contains(expectedMember));
        }

        @Test
        public void testCrud() throws Exception {

            // Members should be empty
            verifyNumberOfPoolMembers(0);

            // Post
            DtoPoolMember poolMember = createStockPoolMember();
            verifyNumberOfPoolMembers(1);

            // Post another
            DtoPoolMember poolMember2 = createStockPoolMember();
            verifyNumberOfPoolMembers(2);

            // Get and check
            DtoPoolMember newPoolMember = getPoolMember(poolMember.getUri());
            Assert.assertEquals(poolMember, newPoolMember);
            newPoolMember = getPoolMember(poolMember2.getUri());
            Assert.assertEquals(poolMember2, newPoolMember);

            // Delete
            deletePoolMember(poolMember.getUri());
            verifyNumberOfPoolMembers(1);
            deletePoolMember(poolMember2.getUri());
            verifyNumberOfPoolMembers(0);
        }

        @Test
        public void assertCreateAddsReferences() {
            DtoLoadBalancer loadBalancer = createStockLoadBalancer();
            DtoPool pool = createStockPool(loadBalancer.getId());
            checkBackrefs(pool.getUri()); // No members.

            DtoPoolMember member1 = createStockPoolMember(pool.getId());
            assertEquals(pool.getUri(), member1.getPool());
            checkBackrefs(pool.getUri(), member1);

            DtoPoolMember member2 = createStockPoolMember(pool.getId());
            assertEquals(pool.getUri(), member2.getPool());
            checkBackrefs(pool.getUri(), member1, member2);
        }

        @Test
        public void assertUpdateUpdatesReferences() {
            DtoLoadBalancer loadBalancer = createStockLoadBalancer();
            DtoPool pool1 = createStockPool(loadBalancer.getId());
            DtoPool pool2 = createStockPool(loadBalancer.getId());

            DtoPoolMember member1 = createStockPoolMember(pool1.getId());
            DtoPoolMember member2 = createStockPoolMember(pool1.getId());
            checkBackrefs(pool1.getUri(), member1, member2);

            // Switch member2 to pool2.
            member2.setPoolId(pool2.getId());
            member2 = updatePoolMember(member2);
            assertEquals(pool2.getUri(), member2.getPool());
            checkBackrefs(pool1.getUri(), member1);
            checkBackrefs(pool2.getUri(), member2);

            // Switch member1 to pool2.
            member1.setPoolId(pool2.getId());
            member1 = updatePoolMember(member1);
            assertEquals(pool2.getUri(), member1.getPool());
            checkBackrefs(pool1.getUri()); // No members
            checkBackrefs(pool2.getUri(), member1, member2);
        }

        @Test
        public void testDeletePoolMemberRemovesReferencesFromPool() {
            DtoLoadBalancer loadBalancer = createStockLoadBalancer();
            DtoPool pool = createStockPool(loadBalancer.getId());
            DtoPoolMember member1 = createStockPoolMember(pool.getId());
            DtoPoolMember member2 = createStockPoolMember(pool.getId());
            checkBackrefs(pool.getUri(), member1, member2);

            deletePoolMember(member1.getUri());
            checkBackrefs(pool.getUri(), member2);

            deletePoolMember(member2.getUri());
            checkBackrefs(pool.getUri()); // No members.
        }

        @Test
        public void testDeletePoolRemovesReferencesFromPoolMembers() {
            DtoLoadBalancer loadBalancer = createStockLoadBalancer();
            DtoPool pool = createStockPool(loadBalancer.getId());
            DtoPoolMember member1 = createStockPoolMember(pool.getId());
            assertEquals(pool.getId(), member1.getPoolId());
            assertEquals(pool.getUri(), member1.getPool());

            DtoPoolMember member2 = createStockPoolMember(pool.getId());
            assertEquals(pool.getId(), member2.getPoolId());
            assertEquals(pool.getUri(), member2.getPool());

            deletePool(pool.getUri());
            // Strongly associated resources are deleted by cascading.
            dtoWebResource.getAndVerifyNotFound(member1.getUri(),
                    VendorMediaType.APPLICATION_POOL_MEMBER_JSON);
            dtoWebResource.getAndVerifyNotFound(member2.getUri(),
                    VendorMediaType.APPLICATION_POOL_MEMBER_JSON);
        }

        @Test
        public void testCreateWithDuplicateId() {
            DtoPoolMember member1 = createStockPoolMember();
            DtoPoolMember member2 = getStockPoolMember();
            member2.setId(member1.getId());
            DtoError error = dtoWebResource.postAndVerifyError(
                    topLevelPoolMembersUri, APPLICATION_POOL_MEMBER_JSON,
                    member2, CONFLICT);
            assertErrorMatches(
                    error, RESOURCE_EXISTS, "pool member", member1.getId());
        }

        @Test
        public void testCreateWithBadPoolId() {
            DtoPoolMember member = getStockPoolMember();
            member.setPoolId(UUID.randomUUID());
            DtoError error = dtoWebResource.postAndVerifyError(
                    topLevelPoolMembersUri, APPLICATION_POOL_MEMBER_JSON,
                    member, NOT_FOUND);
            assertErrorMatches(
                    error, RESOURCE_NOT_FOUND, "pool", member.getPoolId());
        }

        @Test
        public void testCreateWithNegativeWeight() {
            DtoPoolMember member = getStockPoolMember();
            member.setWeight(-1);
            DtoError error = dtoWebResource.postAndVerifyError(
                    topLevelPoolMembersUri, APPLICATION_POOL_MEMBER_JSON,
                    member, BAD_REQUEST);
            assertErrorMatches(error, POOL_MEMBER_WEIGHT_NEGATIVE);
        }

        @Test
        public void testGetWithBadPoolMemberId() throws Exception {
            UUID id = UUID.randomUUID();
            DtoError error = dtoWebResource.getAndVerifyNotFound(
                    addIdToUri(topLevelPoolMembersUri, id),
                    APPLICATION_POOL_MEMBER_JSON);
            assertErrorMatches(error, RESOURCE_NOT_FOUND, "pool member", id);
        }

        @Test
        public void testUpdateWithBadPoolMemberId() throws Exception {
            DtoPoolMember member = createStockPoolMember();
            member.setId(UUID.randomUUID());
            member.setUri(addIdToUri(topLevelPoolMembersUri, member.getId()));
            DtoError error = dtoWebResource.putAndVerifyError(member.getUri(),
                    APPLICATION_POOL_MEMBER_JSON, member, NOT_FOUND);
            assertErrorMatches(error, RESOURCE_NOT_FOUND,
                               "pool member", member.getId());
        }

        @Test
        public void testUpdateWithBadPoolId() {
            DtoPoolMember member = createStockPoolMember();
            member.setPoolId(UUID.randomUUID());
            DtoError error = dtoWebResource.putAndVerifyError(member.getUri(),
                    APPLICATION_POOL_MEMBER_JSON, member, NOT_FOUND);
            assertErrorMatches(error, RESOURCE_NOT_FOUND, "pool", member.getPoolId());
        }

        @Test
        public void testUpdateWithNegativeWeight() {
            DtoPoolMember member = createStockPoolMember();
            member.setWeight(-1);
            DtoError error = dtoWebResource.putAndVerifyError(member.getUri(),
                    APPLICATION_POOL_MEMBER_JSON, member, BAD_REQUEST);
            assertErrorMatches(error, POOL_MEMBER_WEIGHT_NEGATIVE);
        }

        @Test
        public void testUpdateWeight() {
            DtoPoolMember member = getStockPoolMember();
            member.setWeight(0); // Should default to 1.
            member = postPoolMember(member);
            assertEquals(1, member.getWeight());

            // Update to 5.
            member.setWeight(5);
            updatePoolMember(member);
            member = getPoolMember(member.getUri());
            assertEquals(5, member.getWeight());

            // Update with the default (1).
            member.setWeight(0);
            updatePoolMember(member);
            member = getPoolMember(member.getUri());
            assertEquals(1, member.getWeight());
        }

        @Test
        public void testDeleteWithBadPoolId() throws Exception {
            // Succeeds because delete is idempotent.
            deletePoolMember(addIdToUri(topLevelPoolMembersUri, UUID.randomUUID()));
        }
    }
}
