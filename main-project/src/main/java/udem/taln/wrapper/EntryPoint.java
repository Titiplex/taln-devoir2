package udem.taln.wrapper;

public class EntryPoint {
    private volatile WrapperInterface py;

    public void registerPythonObject(WrapperInterface obj) {
        this.py = obj;
    }

    public boolean isPythonRegistered() {
        return py != null;
    }

    public WrapperInterface yf() {
        return py;
    }
}