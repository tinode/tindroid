package co.tinode.tinodesdk.model;

import java.io.Serializable;

/**
 * Part of Meta server response
 */
public class DelValues implements Serializable {
    public Integer clear;
    public MsgRange[] delseq;

    public DelValues() {}
}
