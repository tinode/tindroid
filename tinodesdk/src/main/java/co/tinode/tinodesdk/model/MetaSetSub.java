package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Parameter of MsgSetMeta
 */
@JsonInclude(NON_DEFAULT)
public class MetaSetSub implements Serializable {
    public String user;
    public String mode;

    public MetaSetSub() {}

    public MetaSetSub(String mode) {
        this.user = null;
        this.mode = mode;
    }

    public MetaSetSub(String user, String mode) {
        this.user = user;
        this.mode = mode;
    }
}
