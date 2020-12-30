package co.tinode.tindroid.media;

import android.content.Context;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import co.tinode.tinodesdk.model.Drafty;

public abstract class AbstractDraftyFormatter<T extends AbstractDraftyFormatter.TreeNode>
        implements Drafty.Formatter<AbstractDraftyFormatter.TreeNode> {

    protected final TextView mContainer;
    // Maximum width of the container TextView. Max height is maxWidth * 0.75.

    protected AbstractDraftyFormatter(final TextView container) {
        mContainer = container;
    }

    protected abstract T handleStrong(Object content);

    protected abstract T handleEmphasized(Object content);

    protected abstract T handleDeleted(Object content);

    protected abstract T handleCode(Object content);

    protected abstract T handleHidden(Object content);

    protected abstract T handleLineBreak();

    // URL.
    protected abstract T handleLink(final Context ctx, Object content, final Map<String, Object> data);

    // Mention @user.
    protected abstract T handleMention(final Context ctx, Object content, final Map<String, Object> data);

    // Hashtag #searchterm.
    protected abstract T handleHashtag(final Context ctx, Object content, final Map<String, Object> data);

    // Embedded image.
    protected abstract T handleImage(final Context ctx, Object content, final Map<String, Object> data);

    // File attachment.
    protected abstract T handleAttachment(final Context ctx, final Map<String, Object> data);

    // Button: clickable form element.
    protected abstract T handleButton(final Context ctx, final Map<String, Object> data, final Object content);

    // Grouping of form elements.
    protected abstract T handleFormRow(final Context ctx, final Map<String, Object> data, final Object content);

    // Interactive form.
    protected abstract T handleForm(final Context ctx, final Map<String, Object> data, final Object content);

    // Unknown or unsupported element.
    protected abstract T handleUnknown(final Context ctx, final Object content, final Map<String, Object> data);

    // Unstyled content
    protected abstract T handlePlain(Object content);

    @Override
    public TreeNode apply(final String tp, final Map<String, Object> data, final Object content) {
        if (tp != null) {
            T span;
            switch (tp) {
                case "ST":
                    span = handleStrong(content);
                    break;
                case "EM":
                    span = handleEmphasized(content);
                    break;
                case "DL":
                    span = handleDeleted(content);
                    break;
                case "CO":
                    span = handleCode(content);
                    break;
                case "HD":
                    // Hidden text
                    span = handleHidden(content);
                    break;
                case "BR":
                    span = handleLineBreak();
                    break;
                case "LN":
                    span = handleLink(mContainer.getContext(), content, data);
                    break;
                case "MN":
                    span = handleMention(mContainer.getContext(), content, data);
                    break;
                case "HT":
                    span = handleHashtag(mContainer.getContext(), content, data);
                    break;
                case "IM":
                    // Additional processing for images
                    span = handleImage(mContainer.getContext(), content, data);
                    break;
                case "EX":
                    // Attachments; attachments cannot have sub-elements.
                    span = handleAttachment(mContainer.getContext(), data);
                    break;
                case "BN":
                    // Button
                    span = handleButton(mContainer.getContext(), data, content);
                    break;
                case "FM":
                    // Form
                    span = handleForm(mContainer.getContext(), data, content);
                    break;
                case "RW":
                    // Form element formatting is dependent on element content.
                    span = handleFormRow(mContainer.getContext(), data, content);
                    break;
                default:
                    // Unknown element
                    span = handleUnknown(mContainer.getContext(), content, data);
            }
            return span;
        }
        return handlePlain(content);
    }

    // Structure representing Drafty as a tree of formatting nodes.
    public static class TreeNode {
        protected CharSequence text;
        protected List<TreeNode> children;

        protected TreeNode() {
            text = null;
            children = null;
        }

        protected TreeNode(CharSequence content) {
            this.text = content;
        }

        protected TreeNode(Object content) {
            assignContent(content);
        }

        @SuppressWarnings("unchecked")
        private void assignContent(Object content) {
            if (content == null) {
                return;
            }
            if (content instanceof CharSequence) {
                text = (CharSequence) content;
            } else if (content instanceof List) {
                children = (List<TreeNode>) content;
            } else if (content instanceof TreeNode) {
                if (children == null) {
                    children = new ArrayList<>();
                }
                children.add((TreeNode) content);
            } else {
                throw new IllegalArgumentException("Invalid content");
            }
        }

        protected void addNode(TreeNode node) {
            if (node == null) {
                return;
            }

            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(node);
        }

        protected void addNode(CharSequence content) {
            if (content == null) {
                return;
            }
            addNode(new TreeNode(content));
        }

        protected boolean isPlain() {
            return text != null;
        }

        protected boolean hasChildren() {
            return children != null;
        }

        protected boolean isEmpty() {
            return (text == null || text.equals("")) &&
                    (children == null || children.size() == 0);
        }

        protected CharSequence getText() {
            return text;
        }
        protected List<TreeNode> getChildren() {
            return children;
        }
    }
}
