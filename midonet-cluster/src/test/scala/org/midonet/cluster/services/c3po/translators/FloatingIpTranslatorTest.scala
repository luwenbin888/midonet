/*
 * Copyright 2015 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.cluster.services.c3po.translators

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import org.midonet.cluster.models.Commons.{IPAddress, IPSubnet, UUID}
import org.midonet.cluster.models.ModelsUtil._
import org.midonet.cluster.models.Neutron.FloatingIp
import org.midonet.cluster.models.Topology.{Port, Router, Rule}
import org.midonet.cluster.services.c3po.C3POStorageManager._
import org.midonet.cluster.services.c3po.midonet.{CreateNode, DeleteNode}
import org.midonet.cluster.util.UUIDUtil.{fromProto, randomUuidProto}
import org.midonet.cluster.util.{IPAddressUtil, IPSubnetUtil, UUIDUtil}
import RouterTranslator.tenantGwPortId
import org.midonet.packets.{IPv4Addr, MAC}

class FloatingIpTranslatorTestBase extends TranslatorTestBase with ChainManager
                                                              with OpMatchers {
    protected var translator: FloatingIpTranslator = _

    // Floating IP data setup
    protected val fipId = randomUuidProto
    protected val fipIdThatDoesNotExist = randomUuidProto
    protected val tntRouterId = randomUuidProto
    protected val fipPortId = randomUuidProto
    protected val fipIpAddr = IPAddressUtil.toProto("10.10.10.1")
    protected val fipIpSubnet = IPSubnetUtil.fromAddr(fipIpAddr)
    protected val fipFixedIpAddr = IPAddressUtil.toProto("192.168.1.1")
    protected val fipFixedIpSubnet = IPSubnetUtil.fromAddr(fipFixedIpAddr)

    protected val unboundFip = nFloatingIpFromTxt(s"""
        id { $fipId }
        floating_ip_address { $fipIpAddr }
        router_id { $tntRouterId }
        """)

    protected def fip(routerId: UUID = tntRouterId,
                      portId: UUID = fipPortId,
                      fixedIp: IPAddress = fipFixedIpAddr) =
        nFloatingIpFromTxt(s"""
            id { $fipId }
            floating_ip_address { $fipIpAddr }
            router_id { $routerId }
            port_id { $portId }
            fixed_ip_address { $fixedIp }
        """)
    protected val boundFip = fip()

    // Main tenant router setup
    protected val externalNetworkId = randomUuidProto
    protected val nTntRouterGatewayPortId = randomUuidProto
    protected val mTntRouterGatewayPortId =
        tenantGwPortId(nTntRouterGatewayPortId)
    protected val tntRouterGatewayPortMac = "11:22:33:fe:dc:ba"
    protected val tntRouterInternalPortId = randomUuidProto
    protected val tntRouterInternalPortSubnet =
        IPSubnetUtil.toProto("10.10.11.0/24")
    protected val tntRouterInChainId = inChainId(tntRouterId)
    protected val tntRouterOutChainId =outChainId(tntRouterId)

    protected val mTntRouter = mRouterFromTxt(s"""
        id { $tntRouterId }
        port_ids { $tntRouterInternalPortId }
        port_ids { $nTntRouterGatewayPortId }
        inbound_filter_id { $tntRouterInChainId }
        outbound_filter_id { $tntRouterOutChainId }
        """)

    protected val nTntRouterNoGwPort = nRouterFromTxt(s"""
        id { $tntRouterId }
        """)
    protected val mTntRouterNoGwPort = Router.newBuilder
        .setId(tntRouterId)
        .addPortIds(tntRouterInternalPortId)
        .build()

    protected val nTntRouter = nRouterFromTxt(s"""
        id { $tntRouterId }
        gw_port_id { $nTntRouterGatewayPortId }
        """)

    protected val nTntRouterGatewayPort = nPortFromTxt(s"""
        id { $nTntRouterGatewayPortId }
        network_id { $externalNetworkId }
        mac_address: "$tntRouterGatewayPortMac"
        device_owner: ROUTER_GATEWAY
        """)
    protected val mTntRouterGatwewayPort = Port.newBuilder
        .setId(mTntRouterGatewayPortId)
        .setPortSubnet(IPSubnetUtil.fromAddr(fipIpAddr, 24))
        .build()
    protected val mTntRouterInternalPort = Port.newBuilder
        .setId(tntRouterInternalPortId)
        .setRouterId(tntRouterId)
        .setPortSubnet(tntRouterInternalPortSubnet)
        .build()

    protected val fipArpEntryPath = stateTableStorage.bridgeArpEntryPath(
            externalNetworkId, IPv4Addr(fipIpAddr.getAddress),
            MAC.fromString(tntRouterGatewayPortMac))

    protected val snatRuleId = RouteManager.fipSnatRuleId(fipId)
    protected val dnatRuleId = RouteManager.fipDnatRuleId(fipId)
    protected val reverseIcmpDnatRuleId = RouteManager.fipReverseDnatRuleId(fipId)

    protected def snatRule(gatewayPortId: UUID, sourcePortId: UUID,
                           fixedIpSubnet: IPSubnet = fipFixedIpSubnet) =
        mRuleFromTxt(s"""
            id { $snatRuleId }
            type: NAT_RULE
            action: ACCEPT
            condition {
                out_port_ids { ${tenantGwPortId(gatewayPortId)} }
                nw_src_ip { $fixedIpSubnet }
                fragment_policy: ANY
            }
            fip_port_id { $sourcePortId }
            nat_rule_data {
                nat_targets {
                    nw_start { $fipIpAddr }
                    nw_end { $fipIpAddr }
                    tp_start: 0
                    tp_end: 0
                }
                dnat: false
            }
        """)
    protected val snat = snatRule(nTntRouterGatewayPortId, fipPortId)

    protected def dnatRule(gatewayPortId: UUID, destPortId: UUID,
                           fixedIpAddr: IPAddress = fipFixedIpAddr) =
        mRuleFromTxt(s"""
            id { $dnatRuleId }
            type: NAT_RULE
            action: ACCEPT
            condition {
                in_port_ids { ${tenantGwPortId(gatewayPortId)} }
                nw_dst_ip { $fipIpSubnet }
                fragment_policy: ANY
            }
            fip_port_id { $destPortId }
            nat_rule_data {
                nat_targets {
                    nw_start { $fixedIpAddr }
                    nw_end { $fixedIpAddr }
                    tp_start: 0
                    tp_end: 0
                }
                dnat: true
            }
        """)
    protected val dnat = dnatRule(nTntRouterGatewayPortId, fipPortId)

    protected def icmpReverseDnatRule(gatewayPortId: UUID, destPortId: UUID,
                                      fixedIpSubnet: IPSubnet = fipFixedIpSubnet) =
        mRuleFromTxt(s"""
            id { $reverseIcmpDnatRuleId }
            type: NAT_RULE
            action: CONTINUE
            condition {
                out_port_ids { ${tenantGwPortId(gatewayPortId)} }
                fragment_policy: ANY
                icmp_data_dst_ip { $fixedIpSubnet }
            }
            fip_port_id { $destPortId }
            nat_rule_data {
                nat_targets {
                    nw_start { $fipIpAddr }
                    nw_end { $fipIpAddr }
                    tp_start: 0
                    tp_end: 0
                }
                dnat: true
            }
        """)
    protected val icmpReverseDnat = icmpReverseDnatRule(nTntRouterGatewayPortId,
                                                        fipPortId)

    protected val inChainDummyRuleIds = """
        rule_ids { msb: 1 lsb: 2 }
        rule_ids { msb: 3 lsb: 4 }
        """
    protected val outChainDummyRuleIds = """
        rule_ids { msb: 5 lsb: 6 }
        rule_ids { msb: 7 lsb: 8 }
        """

    protected val tntRouterInChain = mChainFromTxt(s"""
        id { $tntRouterInChainId }
        $inChainDummyRuleIds
        """)

    protected val tntRouterOutChain = mChainFromTxt(s"""
        id { $tntRouterOutChainId }
        $outChainDummyRuleIds
        """)

    protected val inChainWithDnat = mChainFromTxt(s"""
        id { $tntRouterInChainId }
        rule_ids { $dnatRuleId }
        $inChainDummyRuleIds
        """)

    protected val outChainWithSnatAndReverseIcmpDnat = mChainFromTxt(s"""
        id { $tntRouterOutChainId }
        rule_ids { $reverseIcmpDnatRuleId }
        rule_ids { $snatRuleId }
        $outChainDummyRuleIds
        """)
}

/**
 * Tests a Neutron Floating IP Create translation.
 */
@RunWith(classOf[JUnitRunner])
class FloatingIpTranslatorCreateTest extends FloatingIpTranslatorTestBase {
    before {
        initMockStorage()
        translator = new FloatingIpTranslator(storage, stateTableStorage,
                                              pathBldr)

        bind(tntRouterId, nTntRouter)
        bind(tntRouterId, mTntRouter)
        bind(tntRouterInChainId, tntRouterInChain)
        bind(tntRouterOutChainId, tntRouterOutChain)
        bind(nTntRouterGatewayPortId, nTntRouterGatewayPort)
        bind(mTntRouterGatewayPortId, mTntRouterGatwewayPort)
        bindAll(Seq(tntRouterInternalPortId), Seq(mTntRouterInternalPort))
    }

    "Unassociated floating IP" should "not create anything" in {
        val midoOps = translator.translate(Create(unboundFip))

        midoOps shouldBe empty
    }

    "Associated floating IP" should "create ARP entry and NAT rules" in {
        val midoOps = translator.translate(Create(boundFip))

        midoOps should contain inOrderOnly (CreateNode(fipArpEntryPath),
                                            Create(snat),
                                            Create(dnat),
                                            Create(icmpReverseDnat),
                                            Update(inChainWithDnat),
                                            Update(outChainWithSnatAndReverseIcmpDnat))
    }

    "Tenant router for floating IP" should "throw an exception if it doesn't " +
    "have a gateway port assigned" in {
        bind(tntRouterId, nTntRouterNoGwPort)
        bind(tntRouterId, mTntRouterNoGwPort)
        val te = intercept[TranslationException] {
            translator.translate(Create(boundFip))
        }
        te.getCause should not be null
        te.getCause match {
            case ise: IllegalStateException if ise.getMessage != null =>
                ise.getMessage should startWith (
                    s"Router ${UUIDUtil.fromProto(tntRouterId)} has no port")
            case e => fail("Expected an IllegalStateException.", e)
        }
    }
}

@RunWith(classOf[JUnitRunner])
class FloatingIpTranslatorUpdateTest extends FloatingIpTranslatorTestBase {
    protected val tntRouter2Id = randomUuidProto
    protected val nTntRouter2GwPortId = randomUuidProto
    protected val mTntRouter2GwPortId = tenantGwPortId(nTntRouter2GwPortId)
    protected val tntRouter2InChainId = inChainId(tntRouter2Id)
    protected val tntRouter2OutChainId =outChainId(tntRouter2Id)
    protected val fipPort2Id = randomUuidProto
    protected val fixedIp2 = IPAddressUtil.toProto("192.168.1.10")
    protected val fixedIpSubnet2 = IPSubnetUtil.fromAddr(fixedIp2)
    protected val fipMovedRtr2 = fip(tntRouter2Id)
    protected val fipMovedPort2 = fip(portId = fipPort2Id, fixedIp = fixedIp2)
    protected val fipMovedRtr2Port2 =
        fip(routerId = tntRouter2Id, portId = fipPort2Id, fixedIp = fixedIp2)
    protected val snatRtr2 = snatRule(nTntRouter2GwPortId, fipPortId)
    protected val icmpReverseDNatRtr2 = icmpReverseDnatRule(nTntRouter2GwPortId, fipPortId)
    protected val dnatRtr2 = dnatRule(nTntRouter2GwPortId, fipPortId)
    protected val snatPort2 =
        snatRule(nTntRouterGatewayPortId, fipPort2Id, fixedIpSubnet2)
    protected val dnatPort2 =
        dnatRule(nTntRouterGatewayPortId, fipPort2Id, fixedIp2)
    protected val icmpReverseDNatPort2 = icmpReverseDnatRule(
        nTntRouterGatewayPortId, fipPort2Id, fixedIpSubnet2)

    protected val snatRtr2Port2 =
        snatRule(nTntRouter2GwPortId, fipPort2Id, fixedIpSubnet2)
    protected val dnatRtr2Port2 =
        dnatRule(nTntRouter2GwPortId, fipPort2Id, fixedIp2)
    protected val icmpReverseDNatRtr2Port2 = icmpReverseDnatRule(
        nTntRouter2GwPortId, fipPort2Id, fixedIpSubnet2)

    protected val nTntRouter2 = nRouterFromTxt(s"""
        id { $tntRouter2Id }
        gw_port_id { $nTntRouter2GwPortId }
        """)

    protected val tntRouter2GwPortMac = "77:88:99:ab:cc:ba"
    protected val nTntRouter2GwPort = nPortFromTxt(s"""
        id { $nTntRouter2GwPortId }
        network_id { $externalNetworkId }
        mac_address: "$tntRouter2GwPortMac"
        device_owner: ROUTER_GATEWAY
        """)
    protected val mTntRouter2GwPort = Port.newBuilder
        .setId(mTntRouter2GwPortId)
        .setPortSubnet(fipIpSubnet)
        .build()

    protected val tntRouter2InChain = mChainFromTxt(s"""
        id { $tntRouter2InChainId }
        $inChainDummyRuleIds
        """)

    protected val tntRouter2OutChain = mChainFromTxt(s"""
        id { $tntRouter2OutChainId }
        $outChainDummyRuleIds
        """)

    protected val tntRouter2InChainWithDnat = mChainFromTxt(s"""
        id { $tntRouter2InChainId }
        rule_ids { $dnatRuleId }
        $inChainDummyRuleIds
        """)

    protected val tntRouter2OutChainWithSnatAndReverseIcmpDnat = mChainFromTxt(s"""
        id { $tntRouter2OutChainId }
        rule_ids { $reverseIcmpDnatRuleId }
        rule_ids { $snatRuleId }
        $outChainDummyRuleIds
        """)

    protected val fipArpEntryPath2 = stateTableStorage.bridgeArpEntryPath(
            externalNetworkId, IPv4Addr(fipIpAddr.getAddress),
            MAC.fromString(tntRouter2GwPortMac))

    before {
        initMockStorage()
        bind(tntRouterInChainId, tntRouterInChain)
        bind(tntRouterOutChainId, tntRouterOutChain)
        bind(nTntRouterGatewayPortId, nTntRouterGatewayPort)
        bind(mTntRouterGatewayPortId, mTntRouterGatwewayPort)
        bind(tntRouter2InChainId, tntRouter2InChain)
        bind(tntRouter2OutChainId, tntRouter2OutChain)
        bind(nTntRouter2GwPortId, nTntRouter2GwPort)
        bind(mTntRouter2GwPortId, mTntRouter2GwPort)
        bindAll(Seq(tntRouterId, tntRouter2Id), Seq(nTntRouter, nTntRouter2))
        bindAll(Seq(tntRouterId, tntRouterId), Seq(nTntRouter, nTntRouter))
        bindAll(Seq(tntRouterInternalPortId), Seq(mTntRouterInternalPort))

        translator = new FloatingIpTranslator(storage, stateTableStorage,
                                              pathBldr)
    }

    "FIP UPDATE that keeps the floating IP unbound" should "do nothing" in {
        bind(fipId, unboundFip)
        val midoOps = translator.translate(Update(unboundFip))

        midoOps shouldBe empty
    }

    "Associating a floating IP to a port" should "add an ARP entry and NAT " +
    "rules" in {
        bind(fipId, unboundFip)
        val midoOps = translator.translate(Update(boundFip))

        midoOps should contain inOrderOnly (
            CreateNode(fipArpEntryPath),
            Create(snat),
            Create(dnat),
            Create(icmpReverseDnat),
            Update(inChainWithDnat),
            Update(outChainWithSnatAndReverseIcmpDnat))
    }

    "Associating a floating IP to a port" should "throw an exception if the " +
    "tenant router doesn't have a gateway port configured" in {
        bind(fipId, unboundFip)
        bind(tntRouterId, nTntRouterNoGwPort)
        bind(tntRouterId, mTntRouterNoGwPort)
        val te = intercept[TranslationException] {
                translator.translate(Update(boundFip))
        }

        te.getCause should not be null
        te.getCause match {
            case ise: IllegalStateException if ise.getMessage != null =>
                ise.getMessage should startWith (
                    s"Router ${UUIDUtil.fromProto(tntRouterId)} has no port")
            case e => fail("Expected an IllegalStateException.", e)
        }
    }

    "Removing a floating IP from a port" should "delete the ARP entry and " +
    "SNAT/DNAT rules, and remove the IDs fo those rules from the inbound / " +
    "outbound chains of the tenant router" in {
        bind(fipId, boundFip)
        val midoOps = translator.translate(Update(unboundFip))

        midoOps should contain inOrderOnly (
            DeleteNode(fipArpEntryPath),
            Delete(classOf[Rule], snatRuleId),
            Delete(classOf[Rule], dnatRuleId),
            Delete(classOf[Rule], reverseIcmpDnatRuleId))
    }

    "UPDATE that keeps the floating IP on the same port/router " should
    "do nothing" in {
        bind(fipId, boundFip)
        val midoOps = translator.translate(Update(boundFip))

        midoOps shouldBe empty
    }

    "UpdateOp that moves the floating IP to a different router" should
    "delete the old ARP entry and NAT rules and create new ones on the new " +
    "router" in {
        bind(fipId, boundFip)
        val midoOps = translator.translate(Update(fipMovedRtr2))

        midoOps should contain inOrderOnly (
            DeleteNode(fipArpEntryPath),
            CreateNode(fipArpEntryPath2),
            Delete(classOf[Rule], snatRuleId),
            Delete(classOf[Rule], dnatRuleId),
            Delete(classOf[Rule], reverseIcmpDnatRuleId),
            Create(snatRtr2),
            Create(dnatRtr2),
            Create(icmpReverseDNatRtr2),
            Update(tntRouter2InChainWithDnat),
            Update(tntRouter2OutChainWithSnatAndReverseIcmpDnat))
    }

    "UpdateOp that moves the floating IP to a different port on the same " +
    "router" should "delete the old NAT rules and create new ones on the " +
    "same router" in {
        bind(fipId, boundFip)
        val midoOps = translator.translate(Update(fipMovedPort2))

        midoOps should contain inOrderOnly (
            Delete(classOf[Rule], snatRuleId),
            Delete(classOf[Rule], dnatRuleId),
            Delete(classOf[Rule], reverseIcmpDnatRuleId),
            Create(snatPort2),
            Create(dnatPort2),
            Create(icmpReverseDNatPort2),
            Update(inChainWithDnat),
            Update(outChainWithSnatAndReverseIcmpDnat))
    }

    "UpdateOp that moves the floating IP to a different port on a different " +
    "router" should "delete the old ARP entry and NAT rules and create new " +
    "ones on the destination router" in {
        bind(fipId, boundFip)
        val midoOps = translator.translate(Update(fipMovedRtr2Port2))

        midoOps should contain inOrderOnly (
            DeleteNode(fipArpEntryPath),
            CreateNode(fipArpEntryPath2),
            Delete(classOf[Rule], snatRuleId),
            Delete(classOf[Rule], dnatRuleId),
            Delete(classOf[Rule], reverseIcmpDnatRuleId),
            Create(snatRtr2Port2),
            Create(dnatRtr2Port2),
            Create(icmpReverseDNatRtr2Port2),
            Update(tntRouter2InChainWithDnat),
            Update(tntRouter2OutChainWithSnatAndReverseIcmpDnat))
    }
}

@RunWith(classOf[JUnitRunner])
class FloatingIpTranslatorDeleteTest extends FloatingIpTranslatorTestBase {
    before {
        initMockStorage()
        translator = new FloatingIpTranslator(storage, stateTableStorage,
                                              pathBldr)

        bind(fipIdThatDoesNotExist, null, classOf[FloatingIp])
    }

    "Deleting an unassociated floating IP" should "not create anything" in {
        bind(fipId, unboundFip)
        val midoOps = translator.translate(Delete(classOf[FloatingIp], fipId))

        midoOps shouldBe empty
    }

    "Deleting an associated floating IP" should "delete the arp entry and " +
    "SNAT/DNAT rules, and remove the IDs fo those rules from the inbound / " +
    "outbound chains of the tenant router" in {
        bind(fipId, boundFip)
        bind(tntRouterId, nTntRouter)
        bind(nTntRouterGatewayPortId, nTntRouterGatewayPort)
        bind(mTntRouterGatewayPortId, mTntRouterGatwewayPort)
        bind(tntRouterInChainId, inChainWithDnat)
        bind(tntRouterOutChainId, outChainWithSnatAndReverseIcmpDnat)
        val midoOps = translator.translate(Delete(classOf[FloatingIp],
                                                          fipId))

        midoOps should contain inOrderOnly (
            DeleteNode(fipArpEntryPath),
            Delete(classOf[Rule], snatRuleId),
            Delete(classOf[Rule], dnatRuleId),
            Delete(classOf[Rule], reverseIcmpDnatRuleId))
    }

    "Deleting a non-existent FIP" should "not raise an error" in {
        val midoOps = translator.translate(Delete(classOf[FloatingIp],
                                                  fipIdThatDoesNotExist))
        midoOps shouldBe empty
    }
}
