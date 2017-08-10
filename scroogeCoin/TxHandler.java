import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class TxHandler {

    UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     * values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        if (tx == null) return false;

        ArrayList<UTXO> utxos = utxoPool.getAllUTXO();
        int outputSize = tx.numOutputs();
        Set<UTXO> utxoStack = new HashSet<>();
        int outputValueSum = 0;
        int inputValueSum = 0;

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);
            if (input == null) {
                return false;
            }

            UTXO transUtxo = new UTXO(input.prevTxHash, input.outputIndex);
            Transaction.Output prevTxOut = utxoPool.getTxOutput(transUtxo);
            if (!Crypto.verifySignature(prevTxOut.address, tx.getRawDataToSign(i), input.signature) ||
                    !utxos.contains(transUtxo) || utxoStack.contains(transUtxo)) {
                return false;
            }
            inputValueSum += prevTxOut.value;
            utxoStack.add(transUtxo);
        }

        for (Transaction.Output out : tx.getOutputs()) {
            if (out.value < 0) {
                return false;
            }
            outputValueSum += out.value;
        }

        if (inputValueSum < outputValueSum) {
            return false;
        }
        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        if (possibleTxs == null) {
            return new Transaction[0];
        }
        ArrayList<Transaction> validTxList = new ArrayList<>();

        for (Transaction tx : possibleTxs) {
            if (isValidTx(tx)) {
                validTxList.add(tx);
                updateUTXOPool(tx);
            }
        }
        Transaction[] resultTx = new Transaction[validTxList.size()];
        return validTxList.toArray(resultTx);

    }

    private void updateUTXOPool(Transaction tx){
        if (tx == null) {
            return;
        }

        for (Transaction.Input input : tx.getInputs()) {
            UTXO transUtxo = new UTXO(input.prevTxHash, input.outputIndex);
            this.utxoPool.removeUTXO(transUtxo);
        }

        byte[] txHash = tx.getHash();

        for (int i = 0; i < tx.numOutputs(); i++) {
            UTXO transUtxo = new UTXO(txHash, i);
            this.utxoPool.addUTXO(transUtxo, tx.getOutput(i));
        }
    }
}
