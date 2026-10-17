package com.ziyuan.Assistant;

import android.graphics.Rect;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;

/** 节点工具 (1:1 移植 Giftedcat/AdSkipHelper) */
public class SkipUtil {

    public static boolean isKeywords(AccessibilityNodeInfo node, String keyWords) {
        CharSequence text = node.getText();
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        return text.toString().contains(keyWords);
    }

    public static String generateNodeDescribe(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            return "null";
        }
        StringBuilder nodeDesc = new StringBuilder();
        nodeDesc.append("class =").append(nodeInfo.getClassName().toString());
        final Rect rect = new Rect();
        nodeInfo.getBoundsInScreen(rect);
        String nodeRect = String.format(" Position=[%d, %d, %d, %d]",
                rect.left, rect.right, rect.top, rect.bottom);
        nodeDesc.append(nodeRect);
        CharSequence id = nodeInfo.getViewIdResourceName();
        if (id != null) {
            nodeDesc.append(" ResourceId=").append(id);
        }
        return nodeDesc.toString();
    }
}
