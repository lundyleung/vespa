// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2.filter;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.athenz.tls.SubjectAlternativeName;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.yahoo.vespa.athenz.tls.SubjectAlternativeName.Type.DNS_NAME;

/**
 * Resolve node from various types of x509 identity certificates.
 *
 * @author bjorncs
 */
class NodeIdentifier {

    static final String TENANT_DOCKER_HOST_IDENTITY = "vespa.vespa.tenant-host";
    static final String PROXY_HOST_IDENTITY = "vespa.vespa.proxy";
    static final String CONFIGSERVER_HOST_IDENTITY = "vespa.vespa.configserver";
    static final String TENANT_DOCKER_CONTAINER_IDENTITY = "vespa.vespa.tenant";
    static final String ZTS_ON_PREM_IDENTITY = "zts.athens.yahoo.com";
    static final String ZTS_AWS_IDENTITY = "zts.athenz.ouroath.com";
    private static final String INSTANCE_ID_DELIMITER = ".instanceid.athenz.";

    private final Zone zone;
    private final NodeRepository nodeRepository;


    private final Supplier<List<Node>> nodeCache;

    NodeIdentifier(Zone zone, NodeRepository nodeRepository) {
        this.zone = zone;
        this.nodeRepository = nodeRepository;
        nodeCache = Suppliers.memoizeWithExpiration(nodeRepository::getNodes, 1, TimeUnit.MINUTES);
    }

    NodePrincipal resolveNode(List<X509Certificate> certificateChain) throws NodeIdentifierException {
        X509Certificate clientCertificate = certificateChain.get(0);
        String subjectCommonName = X509CertificateUtils.getSubjectCommonNames(clientCertificate).stream()
                .findFirst()
                .orElseThrow(() -> new NodeIdentifierException("Certificate subject common name is missing!"));
        if (isAthenzIssued(clientCertificate)) {
            List<SubjectAlternativeName> sans = X509CertificateUtils.getSubjectAlternativeNames(clientCertificate);
            switch (subjectCommonName) {
                case TENANT_DOCKER_HOST_IDENTITY:
                case PROXY_HOST_IDENTITY:
                    return NodePrincipal.withAthenzIdentity(subjectCommonName, getHostFromCalypsoOrAwsCertificate(sans), certificateChain);
                case TENANT_DOCKER_CONTAINER_IDENTITY:
                    return NodePrincipal.withAthenzIdentity(subjectCommonName, getHostFromVespaCertificate(sans), certificateChain);
                case CONFIGSERVER_HOST_IDENTITY:
                default:
                    return NodePrincipal.withAthenzIdentity(subjectCommonName, certificateChain);
            }
        } else if (subjectCommonName.equals(ZTS_ON_PREM_IDENTITY) || subjectCommonName.equals(ZTS_AWS_IDENTITY)) {
            // ZTS treated as a node principal even though its not a Vespa node
            return NodePrincipal.withLegacyIdentity(subjectCommonName, certificateChain);
        } else {
            throw new NodeIdentifierException(String.format("Unknown certificate (subject=%s, issuer=%s)",
                                                            subjectCommonName,
                                                            X509CertificateUtils.getIssuerCommonNames(clientCertificate)));
        }
    }

    private boolean isAthenzIssued(X509Certificate certificate) {
        String issuerCommonName = X509CertificateUtils.getIssuerCommonNames(certificate).stream()
                .findFirst()
                .orElseThrow(() -> new NodeIdentifierException("Certificate issuer common name is missing!"));
        return issuerCommonName.equals("Yahoo Athenz CA") || issuerCommonName.equals("Athenz AWS CA");
    }

    // NOTE: AWS instance id is currently stored as the attribute 'openstack-id' in node repository.
    private String getHostFromCalypsoOrAwsCertificate(List<SubjectAlternativeName> sans) {
        return getHostFromCalypsoCertificate(sans);
    }

    private String getHostFromCalypsoCertificate(List<SubjectAlternativeName> sans) {
        String openstackId = getUniqueInstanceId(sans);
        return nodeCache.get().stream()
                .filter(node -> node.openStackId().equals(openstackId))
                .map(Node::hostname)
                .findFirst()
                .orElseThrow(() -> new NodeIdentifierException(
                        String.format(
                                "Cannot find node with openstack-id '%s' in node repository (SANs=%s)",
                                openstackId,
                                sans.stream().map(SubjectAlternativeName::getValue).collect(Collectors.joining(",", "[", "]")))));
    }

    private String getHostFromVespaCertificate(List<SubjectAlternativeName> sans) {
        // TODO Remove this branch once all BM nodes are gone
        if (sans.stream().anyMatch(san -> san.getValue().endsWith("ostk.yahoo.cloud"))) {
            return getHostFromCalypsoCertificate(sans);
        }
        VespaUniqueInstanceId instanceId = VespaUniqueInstanceId.fromDottedString(getUniqueInstanceId(sans));
        if (!zone.environment().value().equals(instanceId.environment()))
            throw new NodeIdentifierException("Invalid environment: " + instanceId.environment());
        if (!zone.region().value().equals(instanceId.region()))
            throw new NodeIdentifierException("Invalid region(): " + instanceId.region());
        List<Node> applicationNodes =
                nodeRepository.getNodes(ApplicationId.from(instanceId.tenant(), instanceId.application(), instanceId.instance()));
        return applicationNodes.stream()
                .filter(
                        node -> node.allocation()
                                .map(allocation -> allocation.membership().index() == instanceId.clusterIndex()
                                        && allocation.membership().cluster().id().value().equals(instanceId.clusterId()))
                                .orElse(false))
                .map(Node::hostname)
                .findFirst()
                .orElseThrow(() -> new NodeIdentifierException("Could not find any node with instance id: " + instanceId.asDottedString()));
    }

    private static String getUniqueInstanceId(List<SubjectAlternativeName> sans) {
        return sans.stream()
                .filter(san -> san.getType() == DNS_NAME)
                .map(SubjectAlternativeName::getValue)
                .filter(dnsName -> (dnsName.endsWith("yahoo.cloud") || dnsName.endsWith("oath.cloud")) && dnsName.contains(INSTANCE_ID_DELIMITER))
                .map(dnsName -> dnsName.substring(0, dnsName.indexOf(INSTANCE_ID_DELIMITER)))
                .findFirst()
                .orElseThrow(() -> new NodeIdentifierException("Could not find unique instance id from SAN addresses: " + sans));
    }

    static class NodeIdentifierException extends RuntimeException {
        NodeIdentifierException(String message) {
            super(message);
        }
    }

}
