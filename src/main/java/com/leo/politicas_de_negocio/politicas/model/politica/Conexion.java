package com.leo.politicas_de_negocio.politicas.model.politica;

public class Conexion {
    private String origen;
    private String destino;
    private String puertoOrigen;
    private String puertoDestino;

    public Conexion() {}

    public Conexion(String origen, String destino, String puertoOrigen, String puertoDestino) {
        this.origen = origen;
        this.destino = destino;
        this.puertoOrigen = puertoOrigen;
        this.puertoDestino = puertoDestino;
    }

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }
    public String getPuertoOrigen() { return puertoOrigen; }
    public void setPuertoOrigen(String puertoOrigen) { this.puertoOrigen = puertoOrigen; }
    public String getPuertoDestino() { return puertoDestino; }
    public void setPuertoDestino(String puertoDestino) { this.puertoDestino = puertoDestino; }

    public static ConexionBuilder builder() {
        return new ConexionBuilder();
    }

    public static class ConexionBuilder {
        private String origen;
        private String destino;
        private String puertoOrigen;
        private String puertoDestino;

        public ConexionBuilder origen(String origen) { this.origen = origen; return this; }
        public ConexionBuilder destino(String destino) { this.destino = destino; return this; }
        public ConexionBuilder puertoOrigen(String puertoOrigen) { this.puertoOrigen = puertoOrigen; return this; }
        public ConexionBuilder puertoDestino(String puertoDestino) { this.puertoDestino = puertoDestino; return this; }

        public Conexion build() {
            return new Conexion(origen, destino, puertoOrigen, puertoDestino);
        }
    }
}
