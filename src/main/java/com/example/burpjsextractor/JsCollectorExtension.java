package com.example.burpjsextractor;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.sitemap.SiteMapFilter;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.InvocationType;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class JsCollectorExtension implements BurpExtension, HttpHandler, ContextMenuItemsProvider {
    private static final Pattern JS_PATH_PATTERN = Pattern.compile("(?i)\\.js(?:$|[?#])");

    private final AtomicReference<String> targetHost = new AtomicReference<>("");
    private final Set<String> collectedJsUrls = ConcurrentHashMap.newKeySet();

    private Logging logging;
    private MontoyaApi api;
    private JTextField hostField;
    private JTextArea previewArea;
    private JLabel statusLabel;
    private JLabel previewHostLabel;
    private List<String> previewJsPaths = Collections.emptyList();

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        api.extension().setName("JS Host Collector");
        this.logging = api.logging();

        api.http().registerHttpHandler(this);
        api.userInterface().registerContextMenuItemsProvider(this);

        JPanel panel = buildUi(api.userInterface());
        api.userInterface().registerSuiteTab("JS Collector", panel);

        logging.logToOutput("[JS Host Collector] Extension loaded.");
    }

    @Override
    public List<java.awt.Component> provideMenuItems(ContextMenuEvent event) {
        if (!event.isFrom(InvocationType.SITE_MAP_TREE, InvocationType.SITE_MAP_TABLE)) {
            return Collections.emptyList();
        }

        JMenuItem previewPathsItem = new JMenuItem("Preview JS paths (JS Extractor)");
        previewPathsItem.addActionListener(e -> previewSelectedJsPaths(event.selectedRequestResponses()));

        return List.of(previewPathsItem);
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent httpRequestToBeSent) {
        return RequestToBeSentAction.continueWith(httpRequestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        String configuredHost = targetHost.get();
        if (configuredHost == null || configuredHost.isBlank()) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        String requestHost = responseReceived.initiatingRequest().httpService().host();
        if (!configuredHost.equalsIgnoreCase(requestHost)) {
            return ResponseReceivedAction.continueWith(responseReceived);
        }

        String url = responseReceived.initiatingRequest().url();
        if (isJavaScriptResponse(url, responseReceived)) {
            boolean added = collectedJsUrls.add(url);
            if (added) {
                logging.logToOutput("[JS Host Collector] JS found: " + url);
            }
        }

        return ResponseReceivedAction.continueWith(responseReceived);
    }

    private boolean isJavaScriptResponse(String url, HttpResponse response) {
        if (JS_PATH_PATTERN.matcher(url).find()) {
            return true;
        }

        String contentType = headerValue(response, "Content-Type");
        if (contentType == null) {
            return false;
        }

        String lower = contentType.toLowerCase();
        return lower.contains("javascript")
                || lower.contains("ecmascript")
                || lower.contains("application/x-javascript");
    }

    private String headerValue(HttpResponse response, String headerName) {
        return response.headers().stream()
                .filter(header -> header.name().equalsIgnoreCase(headerName))
                .map(header -> header.value())
                .findFirst()
                .orElse(null);
    }

    private JPanel buildUi(UserInterface userInterface) {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));

        hostField = new JTextField();
        hostField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JButton clearButton = new JButton("Clear");
        JButton loadPreviewButton = new JButton("Load JS Preview");
        JButton copyPreviewButton = new JButton("Copy Preview");

        statusLabel = new JLabel("Host: (none)");

        clearButton.addActionListener(e -> {
            collectedJsUrls.clear();
            previewJsPaths = Collections.emptyList();
            previewArea.setText("");
            previewHostLabel.setText("Preview JS paths from Site map | Host: (none)");
            hostField.setText("");
            targetHost.set("");
            statusLabel.setText("Host: (none)");
            logging.logToOutput("[JS Host Collector] URL list cleared.");
        });

        loadPreviewButton.addActionListener(e -> loadPreviewFromHostField());
        copyPreviewButton.addActionListener(e -> copyPreviewToClipboard());

        top.add(new JLabel("Host: "));
        top.add(hostField);
        top.add(loadPreviewButton);
        top.add(copyPreviewButton);
        top.add(clearButton);

        previewArea = new JTextArea();
        previewArea.setEditable(false);
        JScrollPane previewScrollPane = new JScrollPane(previewArea);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        JPanel labels = new JPanel(new BorderLayout());
        previewHostLabel = new JLabel("Preview JS paths from Site map | Host: (none)");
        labels.add(previewHostLabel, BorderLayout.WEST);
        previewScrollPane.setPreferredSize(new Dimension(500, 320));

        center.add(labels, BorderLayout.NORTH);
        center.add(previewScrollPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.WEST);

        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        return root;
    }

    private void previewSelectedJsPaths(List<HttpRequestResponse> selectedRequestResponses) {
        if (selectedRequestResponses == null || selectedRequestResponses.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No hay elementos seleccionados en Site map.", "JS Collector", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<HttpRequestResponse> sourceItems = resolveSourceItemsFromSelection(selectedRequestResponses);
        List<String> paths = extractUniqueJsPaths(sourceItems);
        if (paths.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No se encontraron rutas JavaScript en la seleccion/host.", "JS Collector", JOptionPane.WARNING_MESSAGE);
            return;
        }

        applyPreview(paths, hostLabelFromSelection(selectedRequestResponses));
    }

    private void loadPreviewFromHostField() {
        String host = hostField.getText().trim();
        if (host.isBlank()) {
            host = targetHost.get();
        }

        if (host == null || host.isBlank()) {
            JOptionPane.showMessageDialog(null, "Define un host primero.", "JS Collector", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<HttpRequestResponse> sourceItems = loadHostItemsFromSiteMap(host);
        List<String> paths = extractUniqueJsPaths(sourceItems);
        if (paths.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No se encontraron rutas JavaScript para el host indicado.", "JS Collector", JOptionPane.WARNING_MESSAGE);
            return;
        }

        applyPreview(paths, hostLabelFromInput(host));
    }

    private void applyPreview(List<String> paths, String sourceLabel) {
        previewJsPaths = new ArrayList<>(paths);
        previewArea.setText(String.join("\n", previewJsPaths));
        previewArea.setCaretPosition(0);
        String hostLabel = sourceLabel == null || sourceLabel.isBlank() ? "(unknown)" : sourceLabel;
        hostField.setText(hostLabel);
        targetHost.set(hostLabel);
        previewHostLabel.setText("Preview JS paths from Site map | Host: " + hostLabel);
        logging.logToOutput("[JS Host Collector] Preview lista: " + previewJsPaths.size() + " JS paths from " + hostLabel);
        statusLabel.setText("Host: " + hostLabel + " | Preview JS: " + previewJsPaths.size());
    }

    private String hostLabelFromInput(String hostInput) {
        if (hostInput == null || hostInput.isBlank()) {
            return "(unknown)";
        }

        try {
            String value = hostInput.trim();
            URI uri = value.startsWith("http://") || value.startsWith("https://")
                    ? URI.create(value)
                    : URI.create("https://" + value);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host;
            }
        } catch (Exception ignored) {
            // Fallback to raw input.
        }

        return hostInput.trim();
    }

    private void copyPreviewToClipboard() {
        if (previewJsPaths == null || previewJsPaths.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No hay preview cargada. Pulsa 'Load JS Preview' o usa el menu contextual.", "JS Collector", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String content = String.join("\n", previewJsPaths);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(content), null);
        logging.logToOutput("[JS Host Collector] Copiados " + previewJsPaths.size() + " paths JS desde preview.");
        JOptionPane.showMessageDialog(null, "Copiados " + previewJsPaths.size() + " paths JS al portapapeles.", "JS Collector", JOptionPane.INFORMATION_MESSAGE);
    }

    private List<HttpRequestResponse> loadHostItemsFromSiteMap(String hostInput) {
        List<HttpRequestResponse> result = new ArrayList<>();

        String normalized = hostInput.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            addSiteMapItemsByPrefix(result, normalizeToHostPrefix(normalized));
        } else {
            addSiteMapItemsByPrefix(result, "https://" + normalized);
            addSiteMapItemsByPrefix(result, "http://" + normalized);
        }

        return result;
    }

    private String normalizeToHostPrefix(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String authority = uri.getRawAuthority();
            if (scheme != null && authority != null) {
                return scheme + "://" + authority;
            }
        } catch (Exception ignored) {
            // Fallback to given value if URI parsing fails.
        }

        return value;
    }

    private void addSiteMapItemsByPrefix(List<HttpRequestResponse> target, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }

        try {
            List<HttpRequestResponse> items = api.siteMap().requestResponses(SiteMapFilter.prefixFilter(prefix));
            if (items != null && !items.isEmpty()) {
                target.addAll(items);
            }
        } catch (Exception ex) {
            logging.logToError("[JS Host Collector] SiteMap query failed for prefix " + prefix + ": " + ex.getMessage());
        }
    }

    private List<HttpRequestResponse> resolveSourceItemsFromSelection(List<HttpRequestResponse> selectedRequestResponses) {
        String prefix = hostPrefixFromSelection(selectedRequestResponses);
        if (prefix == null || prefix.isBlank()) {
            return selectedRequestResponses;
        }

        try {
            List<HttpRequestResponse> hostItems = api.siteMap().requestResponses(SiteMapFilter.prefixFilter(prefix));
            if (hostItems != null && !hostItems.isEmpty()) {
                return hostItems;
            }
        } catch (Exception ex) {
            logging.logToError("[JS Host Collector] SiteMap host query failed: " + ex.getMessage());
        }

        return selectedRequestResponses;
    }

    private String hostPrefixFromSelection(List<HttpRequestResponse> selectedRequestResponses) {
        for (HttpRequestResponse rr : selectedRequestResponses) {
            try {
                URI uri = URI.create(rr.request().url());
                String scheme = uri.getScheme();
                String authority = uri.getRawAuthority();
                if (scheme != null && authority != null) {
                    return scheme + "://" + authority;
                }
            } catch (Exception ignored) {
                // Ignore malformed URLs and keep trying.
            }
        }

        return null;
    }

    private String hostLabelFromSelection(List<HttpRequestResponse> selectedRequestResponses) {
        for (HttpRequestResponse rr : selectedRequestResponses) {
            try {
                URI uri = URI.create(rr.request().url());
                String host = uri.getHost();
                if (host != null && !host.isBlank()) {
                    return host;
                }
            } catch (Exception ignored) {
                // Ignore malformed URLs and keep trying.
            }
        }

        return "(unknown)";
    }

    private List<String> extractUniqueJsPaths(List<HttpRequestResponse> requestResponses) {
        Set<String> unique = new LinkedHashSet<>();

        for (HttpRequestResponse rr : requestResponses) {
            try {
                String url = rr.request().url();
                URI uri = URI.create(url);

                String path = uri.getRawPath();
                if (path == null || path.isBlank()) {
                    path = "/";
                }

                String query = uri.getRawQuery();
                if (query != null && !query.isBlank()) {
                    path = path + "?" + query;
                }

                if (JS_PATH_PATTERN.matcher(path).find()) {
                    unique.add(path);
                }
            } catch (Exception ignored) {
                // Ignore malformed requests and keep processing the rest.
            }
        }

        return new ArrayList<>(unique);
    }
}
