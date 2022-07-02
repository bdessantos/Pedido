package com.example.pedido;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private Button btnGerar;
    private Button btnFinalizar;
    private Button mPrint;
    private EditText txtSenhaInicial;
    private TextView lblSenha;

    private ArrayList<Pedido> lstPedido;

    private final String ARQUIVO = "ARQUIVO";
    private final String SENHA_ATUAL = "Senha atual: ";

    // android built in classes for bluetooth operations
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;

    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;

    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Gson gson = new Gson();
        String jsonString = LerArquivoTexto();

        AcharBt();
        AbrirBT();

        btnGerar = (Button) findViewById(R.id.btnGerar);
        btnFinalizar = (Button) findViewById(R.id.btnFinalizar);
        txtSenhaInicial = (EditText) findViewById(R.id.txtSenhaInicial);
        lblSenha = (TextView) findViewById(R.id.lblSenha);

        try {

            if (jsonString == null || jsonString.length() < 1) {
                lstPedido = new ArrayList<>();
            } else {
                Type collectionType = new TypeToken<ArrayList<Pedido>>() {
                }.getType();
                lstPedido = gson.fromJson(jsonString, collectionType);
                AtualizaSenha(lstPedido.get(0).getSenha() + lstPedido.size());
                txtSenhaInicial.setText(String.valueOf(lstPedido.get(0).getSenha()));
                txtSenhaInicial.setEnabled(false);
            }
        } catch (Exception ex) {
            GerarToast(ex.getMessage());
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);
        alertDialog.setTitle("Quantidade");
        alertDialog.setMessage("Entre com a quantidade");


        final EditText input = new EditText(MainActivity.this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);

        input.setLayoutParams(lp);
        alertDialog.setView(input);

        alertDialog.setPositiveButton("Gerar",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        try {

                            String textoQuantidade = input.getText().toString();

                            if (textoQuantidade.length() < 1) {
                                GerarToast("Entre com a quantidade");
                                return;
                            }

                            int senhaInicial = Integer.parseInt(txtSenhaInicial.getText().toString());
                            int senha = Integer.parseInt(lblSenha.getText().toString().replace(SENHA_ATUAL, ""));
                            int quantidade = Integer.parseInt(textoQuantidade);

                            Pedido pedido = new Pedido();
                            pedido.setSenha(senha);
                            pedido.setQuantidade(quantidade);

                            lstPedido.add(pedido);

                            AtualizaSenha(senhaInicial + lstPedido.size());

                            String msg = "   Pedido realizado";
                            msg += "\n";
                            msg += "   Senha:" + pedido.getSenha();
                            msg += "\n";
                            msg += "   Quantidade:" + pedido.getQuantidade();

                            msg += "\n";
                            msg += "\n";
                            msg += "\n";
                            msg += "\n";
                            msg += "\n";
                            msg += "\n";
                            msg += "\n";

                            Imprimir(msg);
                            Thread.sleep(4000);
                            Imprimir(msg);

                            String jsonString = gson.toJson(lstPedido);
                            ExcluirArquivo();
                            GravarArquivoTexto(jsonString);

                            txtSenhaInicial.setEnabled(false);
                            input.setText("");
                        } catch (Exception ex) {
                            GerarToast(ex.getMessage());
                        }
                    }
                });

        alertDialog.setNegativeButton("Cancelar",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        txtSenhaInicial.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {

                int senha = 0;
                try {
                    senha = Integer.parseInt(s.toString());
                } catch (Exception ex) {
                }

                AtualizaSenha(senha);
            }
        });

        btnGerar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                String senha = txtSenhaInicial.getText().toString();

                if (senha.length() < 1) {
                    GerarToast("Escolha uma senha inicial");
                    return;
                }

                if (input.getParent() != null) {
                    ((ViewGroup) input.getParent()).removeView(input);
                }

                try {
                    alertDialog.show();
                } catch (Exception ex) {
                    GerarToast(ex.getMessage());
                }
            }
        });

        btnFinalizar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                int quantidadeVendido = 0;

                Date dataHoraAtual = new Date();
                String data = new SimpleDateFormat("dd/MM/yyyy").format(dataHoraAtual);

                for (int i = 0; i < lstPedido.size(); i ++ ){
                    quantidadeVendido += lstPedido.get(i).getQuantidade();
                }

                String msg = "   Relatorio final";
                msg += "\n";
                msg += "   Quantidade vendido:" + quantidadeVendido;
                msg += "\n";
                msg += "   Data:" + data;

                msg += "\n";
                msg += "\n";
                msg += "\n";
                msg += "\n";
                msg += "\n";
                msg += "\n";
                msg += "\n";

                Imprimir(msg);

                txtSenhaInicial.setEnabled(true);
                ExcluirArquivo();
                lstPedido = new ArrayList<>();
            }
        });

//        mPrint = (Button) findViewById(R.id.mPrint);
//        mPrint.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View mView) {
//
//                // the text typed by the user
//                String msg = "   Paroquia Sao Joao Bosco";
//                msg += "\n";
//                msg += "   Senha:" + 100;
//                msg += "\n";
//                msg += "   Quantidade:" + 50;
//
//                msg += "\n";
//                msg += "\n";
//                msg += "\n";
//                msg += "\n";
//                msg += "\n";
//                msg += "\n";
//                msg += "\n";
//
//                Imprimir(msg);
//
//            }
//        });
    }

    private void GerarToast(String text) {

        Toast toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
        toast.show();
    }

    private void AtualizaSenha(int senha) {
        lblSenha.setText(SENHA_ATUAL + senha);
    }

    private void Imprimir(String texto) {

        try {
            byte[] cc = new byte[]{0x1B,0x21,0x00};  // 0- normal size text
            byte[] bb = new byte[]{0x1B,0x21,0x08};  // 1- only bold text
            byte[] bb2 = new byte[]{0x1B,0x21,0x20}; // 2- bold with medium text
            byte[] bb3 = new byte[]{0x1B,0x21,0x10}; // 3- bold with large text

            mmOutputStream.write(bb3);
            mmOutputStream.write(texto.getBytes());
        }   catch (NullPointerException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // GRAVAR UM ARQUIVO TEXTO
    public void GravarArquivoTexto(String textoGravar) {

        try {

            FileOutputStream out = openFileOutput(ARQUIVO, MODE_APPEND);
            out.write(textoGravar.getBytes());
            out.close();

        } catch (Exception ex) {
            System.out.println(ex);
        }
    }

    // LER UM ARQUIVO TEXTO
    private String LerArquivoTexto() {

        try {
            File file = getFileStreamPath(ARQUIVO);

            if (file.exists()) {

                FileInputStream in = openFileInput(ARQUIVO);
                int tamanho = in.available();
                byte bytes[] = new byte[tamanho];
                in.read(bytes);
                String texto = new String(bytes);

                return texto;
            }

        } catch (Exception ex) {
            System.out.println(ex);
            return null;
        }

        return null;
    }

    // EXCLUIR UM ARQUIVO TEXTO
    public void ExcluirArquivo() {

        try {
            deleteFile(ARQUIVO);
        } catch (Exception ex) {
            System.out.println(ex);
        }
    }

    private void AcharBt() {

        try {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            if (mBluetoothAdapter == null) {
                GerarToast("No bluetooth adapter available");
            }

            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBluetooth = new Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetooth, 0);
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
            }
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {

                    // OJL411MY29I911JH is the name of the bluetooth printer device shown after scan
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                    }

                    if (device.getName().equals("IposPrinter")) {
                        mmDevice = device;
                        break;
                    }
                }
            }

            GerarToast("Bluetooth Device Found");

        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void AbrirBT()  {
        try {
            // Standard SerialPortService ID
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
            }
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmOutputStream = mmSocket.getOutputStream();
            mmInputStream = mmSocket.getInputStream();

            beginListenForData();

            GerarToast("Bluetooth Opened");

        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    void beginListenForData() {
        try {
            final Handler handler = new Handler();

            // This is the ASCII code for a newline character
            final byte delimiter = 10;

            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];

            workerThread = new Thread(new Runnable() {
                public void run() {
                    while (!Thread.currentThread().isInterrupted()
                            && !stopWorker) {

                        try {

                            int bytesAvailable = mmInputStream.available();
                            if (bytesAvailable > 0) {
                                byte[] packetBytes = new byte[bytesAvailable];
                                mmInputStream.read(packetBytes);
                                for (int i = 0; i < bytesAvailable; i++) {
                                    byte b = packetBytes[i];
                                    if (b == delimiter) {
                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        System.arraycopy(readBuffer, 0,
                                                encodedBytes, 0,
                                                encodedBytes.length);
                                        final String data = new String(
                                                encodedBytes, "US-ASCII");
                                        readBufferPosition = 0;

                                        handler.post(new Runnable() {
                                            public void run() {

                                                GerarToast(data);
//                                                myLabel.setText(data);
                                            }
                                        });
                                    } else {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }
                            }

                        } catch (IOException ex) {
                            stopWorker = true;
                        }

                    }
                }
            });

            workerThread.start();

        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}