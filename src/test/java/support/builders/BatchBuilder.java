package support.builders;

import domain.inventory.Batch;

import java.time.LocalDate;

public class BatchBuilder {
    private long id = 1L;
    private String code = "SKU-1";
    private LocalDate expiry = LocalDate.now().plusDays(10);
    private int shelf = 0;
    private int store = 0;
    private int main = 0;

    public BatchBuilder id(long id){ this.id = id; return this; }
    public BatchBuilder code(String code){ this.code = code; return this; }
    public BatchBuilder expiry(LocalDate expiry){ this.expiry = expiry; return this; }
    public BatchBuilder shelf(int shelf){ this.shelf = shelf; return this; }
    public BatchBuilder store(int store){ this.store = store; return this; }
    public BatchBuilder main(int main){ this.main = main; return this; }

    public Batch build(){ return new Batch(id, code, expiry, shelf, store, main); }
}