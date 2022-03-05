package co.tinode.tinsdk.model;

import java.io.Serializable;

/**
 * Part of Meta server response
 */
public class DelValues implements Serializable {
    public Integer clear;
    public MsgRange[] delseq;

    public DelValues() {}
}
