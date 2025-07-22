package io.jenkins.plugins.samesite;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.FormValidation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.stapler.StaplerAccessibleType;
import jenkins.security.stapler.StaplerDispatchable;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

@Extension
public class ActionImpl extends InvisibleAction implements UnprotectedRootAction {
    private Map<UUID, String> data = new HashMap<>(); // TODO Cache expiration
    @Override
    public String getUrlName() {
        return "samesite";
    }

    @POST
    public void doReceiveData(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        final String value = IOUtils.toString(req.getInputStream(), StandardCharsets.UTF_8);
        UUID uuid = UUID.randomUUID();
        data.put(uuid, value);
        rsp.sendRedirect(uuid.toString());
    }

    @StaplerDispatchable
    public Approval getDynamic(String uuid) {
        final UUID theUuid = UUID.fromString(uuid);
        if (data.containsKey(theUuid)) {
            return new Approval(theUuid);
        }
        return null;
    }

    @StaplerAccessibleType
    public static class Approval implements StaplerProxy {
        public static final Logger LOGGER = Logger.getLogger(Approval.class.getName());
        private final UUID uuid;

        public Approval(UUID uuid) {
            this.uuid = uuid;
        }

        @POST
        public HttpResponse doApprove(StaplerRequest2 req) {
            LOGGER.log(Level.INFO, () -> "Approving " + uuid + " ...");
            return FormValidation.ok("Approved!");
        }

        public String getValue() {
            return ExtensionList.lookupSingleton(ActionImpl.class).data.get(uuid);
        }

        @Override
        public Object getTarget() {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return this;
        }
    }

    @Extension
    public static class CrumbExclusionImpl extends CrumbExclusion {
        @Override
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            String pathInfo = request.getPathInfo();
            if ("/samesite/receiveData".equals(pathInfo)) {
                chain.doFilter(request, response);
                return true;
            }
            return false;
        }
    }
}
