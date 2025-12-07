package com.omarflex5.temp.omerflex.service;

import android.util.Log;
import android.webkit.WebView;

public class HtmlPageService {

    /**
     * inject js in view to remove ads
     *
     * @param view
     */
    public static void cleanWebPage(WebView view, boolean withIframe) {
        //delete all iframes  and their sub iframes
        String delIframes = "function deleteIframes() {\n" +
                "  // Get all iframes on the page\n" +
                "  const iframes = document.querySelectorAll('iframe');\n" +
                "\n" +
                "  // Iterate through each iframe\n" +
                "  for (const iframe of iframes) {\n" +
                "    // If the iframe's src is not about:blank\n" +
                "    if (iframe.src !== 'about:blank') {\n" +
                "      // Search for iframes within the found iframe and delete them\n" +
                "      deleteIframesInIframe(iframe);\n" +
                "    } else {\n" +
                "      // If the iframe's src is about:blank, delete the iframe\n" +
                "      iframe.remove();\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "\n" +
                "function deleteIframesInIframe(iframe) {\n" +
                "  // Get all iframes within the given iframe\n" +
                "  const iframesInIframe = iframe.contentDocument.querySelectorAll('iframe');\n" +
                "\n" +
                "  // Iterate through each iframe within the given iframe\n" +
                "  for (const iframeInIframe of iframesInIframe) {\n" +
                "    // Delete the iframe\n" +
                "    iframeInIframe.remove();\n" +
                "  }\n" +
                "}\n" +
                "\n" +
                "// Call the deleteIframes function to delete all iframes on the page\n" +
                "deleteIframes();";

//        view.loadUrl("javascript: " + delIframes + "; deleteIframes();");


// delete all elements out of the body
        String jsBodyRemoveCode = "javascript:(function() { " +
                "  // Get all elements in the document" +
                "  var allElements = document.querySelectorAll('*');" +
                "  " +
                "  // Iterate through all elements" +
                "  for (var i = 0; i < allElements.length; i++) {" +
                "    var element = allElements[i];" +
                "    " +
                "    // Check if the element is outside the body element" +
                "    if (!document.body.contains(element)) {" +
                "      // If the element is outside the body, remove it" +
                "      element.remove();" +
                "    }" +
                "  }" +
                "})();";

//        view.evaluateJavascript(jsBodyRemoveCode, null);


        //remove divs with display block
        view.evaluateJavascript(
                "(function() {\n" +
                        "var elements = document.querySelectorAll('[style*=\"display: block\"], [style*=\"display: block !important\"]');\n" +
                        "\n" +
                        "if (elements && elements.length > 0) {\n" +
                        "    elements.forEach(function (element) {\n" +
                        "        if (element.parentNode) {\n" +
                        "            element.parentNode.removeChild(element);\n" +
                        "        }\n" +
                        "    });\n" +
                        "}\n" +
                        "})();\n",
                null
        );

        //delete ads iframes
        String jsCode_old = "(function() {" +
                "var iframes = [];" +
                "var allIframes = document.querySelectorAll('iframe');" +
                "for (var i = 0; i < allIframes.length; i++) {" +
                "var iframe = allIframes[i];" +
                "iframe.click();" +
                "while (iframe.hasChildNodes()) {\n" +
                "    iframe.removeChild(iframe.firstChild);\n" +
                "}" +
                "if (iframe.getAttribute('src') !== 'about:blank' && iframe.getAttribute('src') !== null) {" +
                "iframes.push({" +
                "src: iframe.getAttribute('src')," +
                "height: iframe.getAttribute('height')," +
                "width: iframe.getAttribute('width')," +
                "});" +
                "}" +
                "else {" +
                "if (iframe.parentNode !== null) {" +
                "  iframe.parentNode.removeChild(iframe);" +
                "}else{" +
                "iframe.remove();" +
                "}" +
                "}" +
                "}" +
                "return iframes;" +
                "})();";

        String jsCode = "(function() {\n" +
                "var iframes = [];\n" +
                "var allIframes = document.querySelectorAll('iframe');\n" +
                "\n" +
                "if (allIframes && allIframes.length > 0) {\n" +
                "    allIframes.forEach(function (iframe) {\n" +
                "        if (!iframe) return; // Ensure iframe is not null\n" +
                "\n" +
                "        iframe.click();\n" +
                "\n" +
                "        while (iframe.hasChildNodes()) {\n" +
                "            if (iframe.firstChild) {\n" +
                "                iframe.removeChild(iframe.firstChild);\n" +
                "            }\n" +
                "        }\n" +
                "\n" +
                "        var src = iframe.getAttribute('src');\n" +
                "        if (src && src !== 'about:blank') {\n" +
                "            iframes.push({\n" +
                "                src: src,\n" +
                "                height: iframe.getAttribute('height') || \"\",\n" +
                "                width: iframe.getAttribute('width') || \"\"\n" +
                "            });\n" +
                "        } else {\n" +
                "            if (iframe.parentNode) {\n" +
                "                iframe.parentNode.removeChild(iframe);\n" +
                "            } else {\n" +
                "                iframe.remove();\n" +
                "            }\n" +
                "        }\n" +
                "    });\n" +
                "}\n" +
                "\n" +
                "return iframes;\n" +
                "})();\n";
        if (withIframe){
            Log.d("TAG", "cleanWebPage: with iframe");
            view.evaluateJavascript(jsCode, null);
        }
    }
}
